package ticketingsystem;

import java.util.concurrent.locks.ReentrantLock;

public class TicketingDS implements TicketingSystem {

	final private int routenum, coachnum, seatnum, sationnum;
	private int[][][] seats;
	private int[][][] remainTicketNum;
	private final int[][] hashDistance;
	private long totTid = 0;

	private ReentrantLock[] reentrantLock;
	private ReentrantLock[] reentrantLock1;

	public TicketingDS(int _routenum, int _coachnum, int _seatnum, int _sationnum, int _threadnum) {
		routenum = _routenum;
		coachnum = _coachnum;
		seatnum = _seatnum;
		sationnum = _sationnum;

		reentrantLock = new ReentrantLock[routenum + 1];
		reentrantLock1 = new ReentrantLock[routenum + 1];
		
		seats = new int[routenum + 1][coachnum + 1][seatnum + 1];
		remainTicketNum = new int[routenum + 1][sationnum + 1][sationnum + 1];
		
		for(int i=1;i<=routenum;++i) {
			remainTicketNum[i][1][sationnum] = coachnum * seatnum;
			reentrantLock[i] = new ReentrantLock();
			reentrantLock1[i] = new ReentrantLock();

			// for(int j=1;j<sationnum;++j) {
			// 	for(int k=i+1;k<=sationnum;++k) {
			// 	}
			// }
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
				if((seats[route][i][j] & curHash) == 0) {
					reentrantLock[route].lock();
					int oldHash = seats[route][i][j];
					if((oldHash & curHash) == 0) {
						seats[route][i][j] = oldHash | curHash;
						ticket.tid = ++totTid;
						int l;
						for(l=departure-1;l>=1;--l) {
							if((oldHash & hashDistance[l][l+1]) != 0) {
								break;
							}
						}
						l++;
						int r;
						for(r=arrival+1;r<=sationnum;++r) {
							if((oldHash & hashDistance[r-1][r]) != 0) {
								break;
							}
						}
						r--;
						
						reentrantLock1[route].lock();
						if(l < departure)
							remainTicketNum[route][l][departure]++;
						if(r > arrival) 
							remainTicketNum[route][arrival][r]++;
						remainTicketNum[route][l][r]--;
						reentrantLock1[route].unlock();

						reentrantLock[route].unlock();
						ticket.coach = i;
						ticket.seat = j;
						return ticket;
					}
					reentrantLock[route].unlock();
				}
			}
		}
		
		return null;
	}

	@Override
	public int inquiry(int route, int departure, int arrival) {
		int cnt = 0;
		reentrantLock1[route].lock();
		for(int i=departure;i>=1;--i) {
			for(int j=arrival;j<=sationnum;++j) {
				cnt += remainTicketNum[route][i][j];
			}
		}
		reentrantLock1[route].unlock();

		return cnt;
	}

	@Override
	public boolean refundTicket(Ticket ticket) {
		int curHash = hashDistance[ticket.departure][ticket.arrival];
		int coach = ticket.coach;
		int seat = ticket.seat;
		int route = ticket.route;

		reentrantLock[route].lock();
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

			reentrantLock1[route].lock();
			if(l < ticket.departure) {
				remainTicketNum[route][l][ticket.departure]--;
			}
			if(r > ticket.arrival) {
				remainTicketNum[route][ticket.arrival][r]--;
			}
			remainTicketNum[route][l][r]++;
			reentrantLock1[route].unlock();

			reentrantLock[route].unlock();
			return true;
		}

		reentrantLock[route].unlock();
		return false;
	}
}
