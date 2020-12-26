package ticketingsystem;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import java.util.concurrent.locks.ReentrantLock;

public class TicketingDS implements TicketingSystem {

	final private int routenum, coachnum, seatnum, sationnum;
	private AtomicInteger[][][] seats;
	private int[][][] remainTicketNum;
	private int[][] hashDistance;
	private AtomicLong totTid = new AtomicLong(0);

	private ReentrantLock[] reentrantLock;
	public TicketingDS(int _routenum, int _coachnum, int _seatnum, int _sationnum, int _threadnum) {
		routenum = _routenum;
		coachnum = _coachnum;
		seatnum = _seatnum;
		sationnum = _sationnum;

		reentrantLock = new ReentrantLock[routenum + 1];
		seats = new AtomicInteger[routenum + 1][coachnum + 1][seatnum + 1];
		remainTicketNum = new int[routenum + 1][sationnum + 1][sationnum + 1];
		
		for(int i=1;i<=routenum;++i) {
			
			reentrantLock[i] = new ReentrantLock();

			remainTicketNum[i][1][sationnum] = coachnum * seatnum;

			for(int j=1;j<=coachnum;++j) {
				for(int k=1;k<=seatnum;++k){
					seats[i][j][k] = new AtomicInteger(0);
				}
			}
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

		reentrantLock[route].lock();
		int curHash = hashDistance[departure][arrival];
		for(int i=1;i<=coachnum; ++i) {
			for(int j=1;j<=seatnum;++j) {
				while(true) {
					int oldHash = seats[route][i][j].get();
					if((oldHash & curHash) == 0) {
						int newHash = oldHash | curHash;
						if(seats[route][i][j].compareAndSet(oldHash, newHash)) {
							ticket.tid = totTid.getAndIncrement();
							ticket.coach = i;
							ticket.seat = j;

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

							if(l < departure)
								remainTicketNum[route][l][departure]++;
							if(r > arrival) 
								remainTicketNum[route][arrival][r]++;
							remainTicketNum[route][l][r]--;

							reentrantLock[route].unlock();
							return ticket;
						}
					}
					else {
						break;
					}
				}
			}
		}
		reentrantLock[route].unlock();
		
		return null;
	}

	@Override
	public int inquiry(int route, int departure, int arrival) {
		int cnt = 0;
		reentrantLock[route].lock();
		for(int i=departure;i>=1;--i) {
			for(int j=arrival;j<=sationnum;++j) {
				cnt += remainTicketNum[route][i][j];
			}
		}
		reentrantLock[route].unlock();

		return cnt;
	}

	@Override
	public boolean refundTicket(Ticket ticket) {
		int curHash = hashDistance[ticket.departure][ticket.arrival];
		int coach = ticket.coach;
		int seat = ticket.seat;
		int route = ticket.route;
		reentrantLock[route].lock();
		while(true) {
			int oldHash = seats[route][coach][seat].get();
			if((oldHash & curHash) == curHash) {
				int newHash = oldHash & (~curHash);
				if(seats[route][coach][seat].compareAndSet(oldHash, newHash)) {

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

					if(l < ticket.departure) {
						remainTicketNum[route][l][ticket.departure]--;
					}
					if(r > ticket.arrival) {
						remainTicketNum[route][ticket.arrival][r]--;
					}
					remainTicketNum[route][l][r]++;

					reentrantLock[route].unlock();
					return true;
				}
			}
			else {
				reentrantLock[route].unlock();
				return false;
			}
		}
	}
}
