package ticketingsystem;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

//座位类
class seat {
    int seat_id;// 座位编号
    int[] seat_map;// 座位是否被占用，0号位置表示该座位是否被使用
    volatile AtomicBoolean use;
}

// 车厢类
class coach {
    int coach_id;
    seat[] seats;
    Map<Integer, AtomicInteger> coachNum;
}

// 列车类
class route {
    int route_id;
    coach[] coachs;
    Map<Integer, AtomicInteger> routeNum;
}

public class TicketingDS implements TicketingSystem {
    int routenum = 5;// 车次总数
    int coachnum = 8;// 列车车厢数目
    int seatnum = 100;// 列车座位数目
    int stationnum = 10;// 车站数目
    int threadnum = 16;// 线程数

    AtomicLong globel_ID;
    route[] routes;// 列车的结构体

    // Lock lock;
    // 构造函数

    public TicketingDS(int routenum, int coachnum, int seatnum, int stationnum, int threadnum) {
        this.routenum = routenum;
        this.coachnum = coachnum;
        this.seatnum = seatnum;
        this.stationnum = stationnum;
        this.threadnum = threadnum;
        globel_ID=new AtomicLong(0);
        init();
    }

    // 初始化所有数据结构
    public void init() {
        // 生成所有列车
        this.routes = new route[routenum];
        for (int i = 0; i < routenum; i++) {
            routes[i] = new route();
            routes[i].route_id = i + 1;
            routes[i].coachs = new coach[coachnum];
            // 生成列车的车厢
            for (int j = 0; j < coachnum; j++) {
                routes[i].coachs[j] = new coach();
                routes[i].coachs[j].coach_id = j + 1;
                routes[i].coachs[j].seats = new seat[seatnum];
                // 生成车厢座位
                for (int k = 0; k < seatnum; k++) {
                    routes[i].coachs[j].seats[k] = new seat();
                    routes[i].coachs[j].seats[k].seat_id = k + 1;
                    routes[i].coachs[j].seats[k].seat_map = new int[stationnum + 1];
                    for (int s = 0; s <= stationnum; s++) {
                        // 初始全部置空
                        routes[i].coachs[j].seats[k].seat_map[s] = 0;
                    }
                    routes[i].coachs[j].seats[k].use = new AtomicBoolean(false);
                }
                routes[i].coachs[j].coachNum = new ConcurrentHashMap<Integer, AtomicInteger>();
                routes[i].coachs[j].coachNum.put(1 * 100 + stationnum, new AtomicInteger(seatnum));
                for (int start = 1; start < stationnum; start++) {
                    for (int end = start + 1; end <= stationnum; end++) {
                        if (start == 1 && end == stationnum)
                            continue;
                        routes[i].coachs[j].coachNum.put(start * 100 + end, new AtomicInteger(0));
                    }
                }
            }
            routes[i].routeNum = new ConcurrentHashMap<Integer, AtomicInteger>();
            routes[i].routeNum.put(1 * 100 + stationnum, new AtomicInteger(seatnum * coachnum));
            for (int start = 1; start < stationnum; start++) {
                for (int end = start + 1; end <= stationnum; end++) {
                    if (start == 1 && end == stationnum)
                        continue;
                    routes[i].routeNum.put(start * 100 + end, new AtomicInteger(0));
                }
            }
        }
    }

    // 买票
    public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
       // 查询车厢是否有余票
       int sel_coach = 0;
       int sel_seat = 0;
       boolean seat_find = false;
       int inquiry_count = coachnum;
       while (inquiry_count-- > 0) {
           boolean coach_find = false;
           sel_coach++;
           for (; sel_coach <= coachnum; sel_coach++) {
               if (inquiry_coach(route, sel_coach, departure, arrival) > 0) {
                   coach_find = true;
                   break;
               }
           }
            if (!coach_find)
                return null;
            for (sel_seat = 1; sel_seat <= seatnum; sel_seat++) {
                if (routes[route - 1].coachs[sel_coach - 1].seats[sel_seat - 1].seat_map[0] == 0 || checkIsUse(
                        routes[route - 1].coachs[sel_coach - 1].seats[sel_seat - 1], departure, arrival)) {
                    // 如果可用，写相应的区间
                    if (routes[route - 1].coachs[sel_coach - 1].seats[sel_seat - 1].use.compareAndSet(false, true)) {
                        // 会出现判断和其他进程在同步进程操作该位置，导致判断出错
                        if (!checkIsUse(routes[route - 1].coachs[sel_coach - 1].seats[sel_seat - 1], departure,
                                arrival)) {
                            routes[route - 1].coachs[sel_coach - 1].seats[sel_seat - 1].use.set(false);
                            continue;
                        }
                        // 记录原数组起末位置
                        int start = 1, end = stationnum;
                        for (int i = departure; i >= 1; i--) {
                            if (routes[route - 1].coachs[sel_coach - 1].seats[sel_seat - 1].seat_map[i] == 1) {
                                start = i + 1;
                                break;
                            }
                        }
                        for (int i = arrival; i <= stationnum; i++) {
                            if (routes[route - 1].coachs[sel_coach - 1].seats[sel_seat - 1].seat_map[i] == 1) {
                                end = i;
                                break;
                            }
                        }
                        // 更新map数组
                        for (int i = departure; i < arrival; i++) {
                            routes[route - 1].coachs[sel_coach - 1].seats[sel_seat - 1].seat_map[i] = 1;
                        }
                        routes[route - 1].coachs[sel_coach - 1].seats[sel_seat - 1].seat_map[0] = 1;
                        seat_find = true;
                        // 更新查询数组
                        updateInqury(routes[route - 1].coachs[sel_coach - 1].coachNum, start, end, departure, arrival);
                        updateInqury(routes[route - 1].routeNum, start, end, departure, arrival);
                        // 恢复use使用标志
                        routes[route - 1].coachs[sel_coach - 1].seats[sel_seat - 1].use.set(false);
                        break;
                    }
                }
            }
            // 找到座位退出循环
            if (seat_find) {
                break;
            }
        }
        // 如果没找到可分配座位，直接返回null
        if (!seat_find || sel_seat == 0)
            return null;
        // 生成票返回
        Ticket ticket = new Ticket();
        ticket.passenger = passenger;
        ticket.route = route;
        ticket.arrival = arrival;
        ticket.departure = departure;
        ticket.coach = sel_coach;
        ticket.seat = sel_seat;
        // 设置tid
        ticket.tid = globel_ID.getAndIncrement();
        return ticket;
    }

    // 查询余票
    public int inquiry(int route, int departure, int arrival) {
        int inquiryNum = 0;
        for (int i = 1; i <= departure; i++) {
            for (int j = arrival; j <= stationnum; j++) {
                AtomicInteger temp_num = routes[route - 1].routeNum.get(i * 100 + j);
                if (temp_num != null)
                    inquiryNum += temp_num.intValue();
            }
        }
        return inquiryNum;
    }

    // 退票
    public boolean refundTicket(Ticket ticket) {
        // 做退票检验
        //// todo
        // 检查票所给区间是否符合条件
        if (!checkExist(ticket))
            return false;
        // 符合条件，改写区间数组
        while (!routes[ticket.route - 1].coachs[ticket.coach - 1].seats[ticket.seat - 1].use.compareAndSet(false,
                true)) {
        }
        // 再次检查是否符合条件
        if (!checkExist(ticket)) {
            // 恢复use使用标志
            routes[ticket.route - 1].coachs[ticket.coach - 1].seats[ticket.seat - 1].use.set(false);
            return false;
        }
        // 更新map数组
        for (int i = ticket.departure; i < ticket.arrival; i++) {
            routes[ticket.route - 1].coachs[ticket.coach - 1].seats[ticket.seat - 1].seat_map[i] = 0;
        }
        // 更新查询数组
        // 向后更新
        refund_back_updata(routes[ticket.route - 1].coachs[ticket.coach - 1].coachNum,
                routes[ticket.route - 1].routeNum,
                routes[ticket.route - 1].coachs[ticket.coach - 1].seats[ticket.seat - 1], ticket.arrival);
        // 向前更新
        refund_front_updata(routes[ticket.route - 1].coachs[ticket.coach - 1].coachNum,
                routes[ticket.route - 1].routeNum,
                routes[ticket.route - 1].coachs[ticket.coach - 1].seats[ticket.seat - 1], ticket.departure,
                ticket.arrival);

        // 恢复use使用标志
        routes[ticket.route - 1].coachs[ticket.coach - 1].seats[ticket.seat - 1].use.set(false);

        return true;
    }

    // 查询车厢余票
    public int inquiry_coach(int route, int coach, int departure, int arrival) {
        int inquiryNum = 0;
        for (int i = 1; i <= departure; i++) {
            for (int j = arrival; j <= stationnum; j++) {
                AtomicInteger temp_num = routes[route - 1].coachs[coach - 1].coachNum.get(i * 100 + j);
                if (temp_num != null)
                    inquiryNum += temp_num.intValue();
            }
        }
        return inquiryNum;
    }

    // 查询区间是否可用
    public boolean checkIsUse(seat check_seat, int departure, int arrival) {
        for (int i = departure; i < arrival; i++) {
            if (check_seat.seat_map[i] == 1)
                return false;
        }
        return true;
    }

    // 更新查询数组区间
    public void updateInqury(Map<Integer, AtomicInteger> count_num, int start, int end, int departure, int arrival) {
        // 修改车厢
        updata_dec_map(count_num, start * 100 + end);
        if (start < departure) {
            updata_add_map(count_num, start * 100 + departure);
        }
        if (end > arrival) {
            updata_add_map(count_num, arrival * 100 + end);
        }

    }

    public boolean checkExist(Ticket ticket) {
        // 遍历座位，是否被占用
        for (int i = ticket.departure; i < ticket.arrival; i++) {
            if (routes[ticket.route - 1].coachs[ticket.coach - 1].seats[ticket.seat - 1].seat_map[i] == 0)
                return false;
        }
        return true;
    }

    public void refund_back_updata(Map<Integer, AtomicInteger> coach_count_num,
            Map<Integer, AtomicInteger> route_count_num, seat seat, int arrival) {
        if (arrival == stationnum)
            return;
        if (seat.seat_map[arrival] == 0) {
            // 合并票
            // 寻找原票区间
            int i;
            for (i = arrival; i < stationnum; ++i) {
                if (seat.seat_map[i] == 1)
                    break;
            }
            // 查询数组中减少该票
            // 更新车厢
            updata_dec_map(coach_count_num, arrival * 100 + i);
            updata_dec_map(route_count_num, arrival * 100 + i);
        }
    }

    public void refund_front_updata(Map<Integer, AtomicInteger> coach_count_num,
            Map<Integer, AtomicInteger> route_count_num, seat seat, int departure, int arrival) {
        int i = departure;
        if (seat.seat_map[departure - 1] == 0) {
            // 前向查询更新
            for (i = departure - 1; i >= 1; --i) {
                if (seat.seat_map[i] == 1)
                    break;
            }
        } else {
            // 保持一致性
            i = i - 1;
        }
        i = i + 1;
        // 存在前置票空，则先删除前置票
        if (i < departure) {
            updata_dec_map(coach_count_num, i * 100 + departure);
            updata_dec_map(route_count_num, i * 100 + departure);
        }
        // 添加新票
        int k;
        for (k = arrival; k < stationnum; ++k) {
            if (seat.seat_map[k] == 1)
                break;
        }
        // 更新查询
        updata_add_map(coach_count_num, i * 100 + k);
        updata_add_map(route_count_num, i * 100 + k);
    }

    public void updata_add_map(Map<Integer, AtomicInteger> map_num, int key) {
        map_num.get(key).getAndIncrement();
    }

    public void updata_dec_map(Map<Integer, AtomicInteger> map_num, int key) {
        map_num.get(key).getAndDecrement();
    }
}