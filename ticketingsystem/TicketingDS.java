package ticketingsystem;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TicketingDS implements TicketingSystem {

	final private int routenum, coachnum, seatnum, sationnum;
	private AtomicInteger[][][] seats;
	private AtomicInteger[][][] remainTicketNum;
	// private ConcurrentHashMap<Long, Ticket> allTickets = new ConcurrentHashMap<>();
	private final int[][] hashDistance;
	private AtomicLong totTid = new AtomicLong(0);
	// private ReentrantLock[][][] seatsLock;
	// private ReentrantLock[] ticketLock;

	public TicketingDS(int _routenum, int _coachnum, int _seatnum, int _sationnum, int _threadnum) {
		routenum = _routenum;
		coachnum = _coachnum;
		seatnum = _seatnum;
		sationnum = _sationnum;
		
		seats = new AtomicInteger[routenum + 1][coachnum + 1][seatnum + 1];
		remainTicketNum = new AtomicInteger[routenum + 1][sationnum + 1][sationnum + 1];
		
		for(int i=1;i<=routenum;++i) {
			
			for(int j=1;j<=coachnum;++j) {
				for(int k=1;k<=seatnum;++k) {
					seats[i][j][k] = new AtomicInteger(0);
					// seatsLock[i][j][k] = new ReentrantLock();
				}
			}

			for(int j=1;j<sationnum;++j) {
				for(int k=j+1;k<=sationnum;++k) {
					remainTicketNum[i][j][k] = new AtomicInteger(0);
				}
			}
			remainTicketNum[i][1][sationnum].set(coachnum * seatnum);
			// ticketLock[i] = new ReentrantLock();
		}

		hashDistance = new int[sationnum + 1][sationnum + 1];
		for(int i=1;i<sationnum;++i) {
			for(int j=i+1;j<=sationnum;++j) {
				hashDistance[i][j] = (1 << (j - 1)) - (1 << (i - 1));
			}
		}
	}

	@Override
	public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
		// if(!(route >= 1 && route <= routenum && departure >= 1 && departure < arrival && arrival <= sationnum))
		// 	return null;

		int curHash = hashDistance[departure][arrival];
		for(int i=1;i<=coachnum; ++i) {
			for(int j=1;j<=seatnum;++j) {
				while(true) {
					int oldHash = seats[route][i][j].get();
					if((oldHash & curHash) != 0){
						break;
					}
					
					int newHash = oldHash | curHash;
					if(seats[route][i][j].compareAndSet(oldHash, newHash)) {					
						int l;
						for(l=departure-1;l>=1;--l) {
							if((oldHash & hashDistance[l][l+1]) != 0) {
								break;
							}
						}
						l++;
						if(l < departure) {
							remainTicketNum[route][l][departure].incrementAndGet();
						}
						int r;
						for(r=arrival+1;r<=sationnum;++r) {
							if((oldHash & hashDistance[r-1][r]) != 0) {
								break;
							}
						}
						r--;
						// ticketLock[route].lock();
						if(r > arrival) {
							remainTicketNum[route][arrival][r].incrementAndGet();
						}

						remainTicketNum[route][l][r].decrementAndGet();

						// ticketLock[route].unlock();

						// seatsLock[route][i][j].unlock();

						Ticket ticket = new Ticket();
						ticket.passenger = passenger;
						ticket.route = route;
						ticket.departure = departure;
						ticket.arrival = arrival;
						ticket.coach = i;
						ticket.seat = j;
						ticket.tid = totTid.getAndIncrement();
						// allTickets.put(ticket.tid, ticket);

						return ticket;
					}
					// seatsLock[route][i][j].unlock();
				}
			}
		}
		
		return null;
	}

	@Override
	public int inquiry(int route, int departure, int arrival) {
		// if(!(route >= 1 && route <= routenum && departure >= 1 && departure < arrival && arrival <= sationnum))
		// 	return 0;
		
		int cnt = 0;
		// ticketLock[route].lock();
		for(int i=1;i<=departure;++i) {
			for(int j=arrival;j<=sationnum;++j) {
				cnt += remainTicketNum[route][i][j].get();
			}
		}
		// ticketLock[route].unlock();

		return cnt;
	}

	@Override
	public boolean refundTicket(Ticket ticket) {
		if(ticket == null)
			return false;
		// Ticket oldTicket = allTickets.get(ticket.tid);
		// if(oldTicket == null) {
		// 	return false;
		// }
		
		int coach = ticket.coach;
		int seat = ticket.seat;
		int route = ticket.route;
		int departure = ticket.departure;
		int arrival = ticket.arrival;

		// if(!(oldTicket.arrival == arrival && oldTicket.coach == coach && oldTicket.departure == departure 
		// && oldTicket.route == route && oldTicket.seat == seat && oldTicket.passenger.equals(ticket.passenger) )){
		// 	return false;
		// }
		// if(allTickets.remove(ticket.tid) == null) {
		// 	return false;
		// }

		int curHash = hashDistance[departure][arrival];
		
		// seatsLock[route][coach][seat].lock();
		while(true) {
			int oldHash = seats[route][coach][seat].get();
			if(seats[route][coach][seat].compareAndSet(oldHash, oldHash & (~curHash))) {
				int l;
				for(l=departure-1;l>=1;--l) {
					if((oldHash & hashDistance[l][l+1]) != 0) {
						break;
					}
				}
				l++;
				if(l < ticket.departure) {
					remainTicketNum[route][l][ticket.departure].decrementAndGet();
				}

				int r;
				for(r=arrival+1;r<=sationnum;++r) {
					if((oldHash & hashDistance[r-1][r]) != 0) {
						break;
					}
				}
				r--;
				if(r > ticket.arrival) {
					remainTicketNum[route][ticket.arrival][r].decrementAndGet();
				}
				// ticketLock[route].lock();

				remainTicketNum[route][l][r].incrementAndGet();

				// ticketLock[route].unlock();

				// seatsLock[route][coach][seat].unlock();
				return true;
			}
		}

		// seatsLock[route][coach][seat].unlock();
	}
}
