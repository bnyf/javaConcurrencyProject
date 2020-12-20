package ticketingsystem;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TicketingDS implements TicketingSystem {

	final private int routenum, coachnum, seatnum, sationnum;
	private AtomicInteger[][][] seats;
	private AtomicLong totTid;

	public TicketingDS(int _routenum, int _coachnum, int _seatnum, int _sationnum, int _threadnum) {
		routenum = _routenum;
		coachnum = _coachnum;
		seatnum = _seatnum;
		sationnum = _sationnum;

		seats = new AtomicInteger[routenum + 1][coachnum + 1][seatnum + 1];
		for(int i=1;i<=routenum;++i) {
			for(int j=1;j<=coachnum;++j) {
				for(int k=1;k<=seatnum;++k){
					seats[i][j][k] = new AtomicInteger(0);
				}
			}
		}
		totTid = new AtomicLong(0);
	}

	private int hashDistance(int departure, int arrival) {
		return (1 << (arrival - 1)) - (1 << (departure - 1));
	}

	@Override
	public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
		Ticket ticket = new Ticket();
		ticket.passenger = passenger;
		ticket.route = route;
		ticket.departure = departure;
		ticket.arrival = arrival;
		int curHash = hashDistance(departure, arrival);
		for(int i=1; i<=coachnum; ++i) {
			for(int j=1;j<=seatnum;++j) {
				while(true) {
					int oldHash = seats[route][i][j].get();
					if((oldHash & curHash) == 0) {
						int newHash = oldHash | curHash;
						if(seats[route][i][j].compareAndSet(oldHash, newHash)) {
							ticket.tid = totTid.getAndIncrement();
							ticket.coach = i;
							ticket.seat = j;
							return ticket;
						}
					}
					else {
						break;
					}
				}
			}
		}
		
		return null;
	}

	@Override
	public int inquiry(int route, int departure, int arrival) {
		int curHash = hashDistance(departure, arrival);
		int cnt = 0;
		for(int i=1;i<=coachnum;++i) {
			for(int j=1;j<=seatnum;++j) {
				int oldHash = seats[route][i][j].get();
				if((oldHash & curHash) == 0) {
					cnt++;
				}
			}

		}
		return cnt;
	}

	@Override
	public boolean refundTicket(Ticket ticket) {
		int curHash = hashDistance(ticket.departure, ticket.arrival);
		int coach = ticket.coach;
		int seat = ticket.seat;
		int route = ticket.route;
		while(true) {
			int oldHash = seats[route][coach][seat].get();
			if((oldHash & curHash) == curHash){
				int newHash = oldHash & (~curHash);
				if(seats[route][coach][seat].compareAndSet(oldHash, newHash)) {
					return true;
				}
			}
			else {
				return false;
			}
		}
	}
}
