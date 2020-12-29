package ticketingsystem;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class TicketingDS implements TicketingSystem {

	final private int routenum, coachnum, seatnum, sationnum;
	private int[][][] seats;
	private int[][][] remainTicketNum;
	private final int[][] hashDistance;
	private AtomicLong totTid = new AtomicLong(0);

	private ReentrantLock[][][] seatsLock;
	private ReentrantLock[] ticketLock;

	public TicketingDS(int _routenum, int _coachnum, int _seatnum, int _sationnum, int _threadnum) {
		routenum = _routenum;
		coachnum = _coachnum;
		seatnum = _seatnum;
		sationnum = _sationnum;

		seatsLock = new ReentrantLock[routenum + 1][coachnum + 1][seatnum + 1];
		ticketLock = new ReentrantLock[routenum + 1];
		
		seats = new int[routenum + 1][coachnum + 1][seatnum + 1];
		remainTicketNum = new int[routenum + 1][sationnum + 1][sationnum + 1];
		
		for(int i=1;i<=routenum;++i) {
			remainTicketNum[i][1][sationnum] = coachnum * seatnum;
			
			for(int j=1;j<=coachnum;++j) {
				for(int k=1;k<=seatnum;++k) {
					seatsLock[i][j][k] = new ReentrantLock();
				}
			}

			ticketLock[i] = new ReentrantLock();
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
		Ticket ticket = new Ticket();
		ticket.passenger = passenger;
		ticket.route = route;
		ticket.departure = departure;
		ticket.arrival = arrival;
		int curHash = hashDistance[departure][arrival];

		for(int i=1;i<=coachnum; ++i) {
			for(int j=1;j<=seatnum;++j) {
				while((seats[route][i][j] & curHash) == 0) {
					int tempHash = seats[route][i][j];
					int l;
					for(l=departure-1;l>=1;--l) {
						if((tempHash & hashDistance[l][l+1]) != 0) {
							break;
						}
					}
					l++;
					int r;
					for(r=arrival+1;r<=sationnum;++r) {
						if((tempHash & hashDistance[r-1][r]) != 0) {
							break;
						}
					}
					r--;
					
					// seat 的修改和票数的修改必须要在一起
					seatsLock[route][i][j].lock();


					int oldHash = seats[route][i][j];
					if(oldHash == tempHash) {
						seats[route][i][j] = oldHash | curHash;						

						ticketLock[route].lock();
						if(l < departure) {
							remainTicketNum[route][l][departure]++;
						}
						remainTicketNum[route][l][r]--;
						if(r > arrival) {
							remainTicketNum[route][arrival][r]++;
						}
						ticketLock[route].unlock();


						seatsLock[route][i][j].unlock();
						ticket.coach = i;
						ticket.seat = j;
						ticket.tid = totTid.getAndIncrement();
						return ticket;
					}
					seatsLock[route][i][j].unlock();
				}
			}
		}
		
		return null;
	}

	@Override
	public int inquiry(int route, int departure, int arrival) {
		int cnt = 0;
		ticketLock[route].lock();

		for(int i=1;i<=departure;++i) {
			for(int j=arrival;j<=sationnum;++j) {
				cnt += remainTicketNum[route][i][j];
			}
		}
		ticketLock[route].unlock();

		return cnt;
	}

	@Override
	public boolean refundTicket(Ticket ticket) {
		int curHash = hashDistance[ticket.departure][ticket.arrival];
		int coach = ticket.coach;
		int seat = ticket.seat;
		int route = ticket.route;

		seatsLock[route][coach][seat].lock();
		int oldHash = seats[route][coach][seat];
		if((oldHash & curHash) == curHash) {
			seats[route][coach][seat] = oldHash & (~curHash);
			int l;
			for(l=ticket.departure-1;l>=1;--l) {
				if((oldHash & hashDistance[l][l+1]) != 0) {
					break;
				}
			}
			l++;

			int r;
			for(r=ticket.arrival+1;r<=sationnum;++r) {
				if((oldHash & hashDistance[r-1][r]) != 0) {
					break;
				}
			}
			r--;


			ticketLock[route].lock();
			if(l < ticket.departure) {
				remainTicketNum[route][l][ticket.departure]--;
			}
			remainTicketNum[route][l][r]++;
			if(r > ticket.arrival) {
				remainTicketNum[route][ticket.arrival][r]--;
			}
			ticketLock[route].unlock();

			seatsLock[route][coach][seat].unlock();
			return true;
		}

		seatsLock[route][coach][seat].unlock();
		return false;
	}
}
