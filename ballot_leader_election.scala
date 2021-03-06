
class GossipLeaderElection(init: Init[GossipLeaderElection]) extends ComponentDefinition {

  val ble = provides[BallotLeaderElection];
  val pl = requires[PerfectLink];
  val timer = requires[Timer];

  val self = init match {
    case Init(s: Address) => s
  }
  val topology = cfg.getValue[List[Address]]("ble.simulation.topology");
  val delta = cfg.getValue[Long]("ble.simulation.delay");
  val majority = (topology.size / 2) + 1;

  private var period = cfg.getValue[Long]("ble.simulation.delay");
  private val ballots = mutable.Map.empty[Address, Long];

  private var round = 0l;
  private var ballot = ballotFromNAddress(0, self);

  private var leader: Option[(Long, Address)] = None;
  private var highestBallot: Long = ballot;

  private def startTimer(delay: Long): Unit = {
    val scheduledTimeout = new ScheduleTimeout(period);
    scheduledTimeout.setTimeoutEvent(CheckTimeout(scheduledTimeout));
    trigger(scheduledTimeout -> timer);
  }

  private def makeLeader(topProcess: (Long, Address)) {
    /* INSERT YOUR CODE HERE */

  }

  private def checkLeader() {
    /* INSERT YOUR CODE HERE */
    var tempBallots=ballots;
    tempBallots= tempBallots + (self.ballot);
    var (topProcess,topBallot) = tempBallots.maxBy(_._2);
    var top:(Address,Long)=(topProcess,topBallot);
    if(topBallot<highestBallot)
    {
      while(ballot<=highestBallot)
      {
        ballot=incrementBallot(ballot);
      }
      leader=None;
    }
    else
    {
      if(Some(top)!=leader)
      {
        highestBallot=topBallot;
        leader=Some(top);
        trigger(BLE_Leader(topProcess,topBallot)->ble);
      }
    }

  }

  ctrl uponEvent {
    case _: Start =>  {
      startTimer(period);
    }
  }

  timer uponEvent {
    case CheckTimeout(_) =>  {
      /* INSERT YOUR CODE HERE */
      if((ballots.size+1)>=majority)
      {
        checkLeader();
      }
      ballots = Map.empty[Address, Long];
      round= round+1;
      for(p<-topology)
      {
        if(p!=self)
        {
          trigger(PL_Send(p,HeartbeatReq(round,highestBallot))->pl);
        }
      }
      startTimer(period);
    }
  }

  pl uponEvent {
    case PL_Deliver(src, HeartbeatReq(r, hb)) =>  {
      /* INSERT YOUR CODE HERE */
      if(hb>highestBallot)
      {
        highestBallot=hb;
      }
      trigger(PL_Send(src,HeartbeatResp(r,ballot))->pl);
    }
    case PL_Deliver(src, HeartbeatResp(r, b)) =>  {
      /* INSERT YOUR CODE HERE */
      if(r==round)
      {
        ballots= ballots + src.b;
      }
      else
      {
        period=period+delta;
      }

    }
  }
}