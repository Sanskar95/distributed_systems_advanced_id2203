
//Define EPFD Implementation
class EPFD(epfdInit: Init[EPFD]) extends ComponentDefinition {

  //EPFD subscriptions
  val timer = requires[Timer];
  val pLink = requires[PerfectLink];
  val epfd = provides[EventuallyPerfectFailureDetector];

  // EPDF component state and initialization

  //configuration parameters
  val self = epfdInit match {case Init(s: Address) => s};
  val topology = cfg.getValue[List[Address]]("epfd.simulation.topology");
  val delta = cfg.getValue[Long]("epfd.simulation.delay");

  //mutable state
  var delay = cfg.getValue[Long]("epfd.simulation.delay");
  var alive = Set(cfg.getValue[List[Address]]("epfd.simulation.topology"): _*);
  var suspected = Set[Address]();
  var seqnum = 0;

  def startTimer(delay: Long): Unit = {
    val scheduledTimeout = new ScheduleTimeout(delay);
    scheduledTimeout.setTimeoutEvent(CheckTimeout(scheduledTimeout));
    trigger(scheduledTimeout -> timer);
  }

  //EPFD event handlers
  ctrl uponEvent {
    case _: Start =>  {
      /* WRITE YOUR CODE HERE  */
      startTimer(delay)
    }
  }

  timer uponEvent {
    case CheckTimeout(_) =>  {
      if (!alive.intersect(suspected).isEmpty) {

        /* WRITE YOUR CODE HERE  */
        delay= delay + delta;

      }

      seqnum = seqnum + 1;

      for (p <- topology) {
        if (!alive.contains(p) && !suspected.contains(p)) {

          /* WRITE YOUR CODE HERE  */
          suspected = suspected + p;
          trigger(Suspect(p) -> epfd);

        } else if (alive.contains(p) && suspected.contains(p)) {
          suspected = suspected - p;
          trigger(Restore(p) -> epfd);
        }
        trigger(PL_Send(p, HeartbeatRequest(seqnum)) -> pLink);
      }
      alive = Set[Address]();
      startTimer(delay);
    }
  }

  pLink uponEvent {
    case PL_Deliver(src, HeartbeatRequest(seq)) =>  {
      trigger(PL_Send(src, HeartbeatRequest(seq)) -> pLink);
    }
    case PL_Deliver(src, HeartbeatReply(seq)) => {
      if((seq == seqnum) || suspected.contains(src)){
        alive = alive +  src
      }
    }
  }
};


