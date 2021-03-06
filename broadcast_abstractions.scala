
class BasicBroadcast(init: Init[BasicBroadcast]) extends ComponentDefinition {

  //BasicBroadcast Subscriptions
  val pLink = requires[PerfectLink];
  val beb = provides[BestEffortBroadcast];

  //BasicBroadcast Component State and Initialization
  val (self, topology) = init match {
    case Init(s: Address, t: Set[Address]@unchecked) => (s, t)
  };

  //BasicBroadcast Event Handlers
  beb uponEvent {
    case x: BEB_Broadcast => {

      for(p<-topology){
        trigger(PL_Send(p,x)->pLink);
      }
    }
  }

  pLink uponEvent {
    case PL_Deliver(src, BEB_Broadcast(payload)) => {

      trigger(BEB_Deliver(src,payload) -> beb);


    }
  }
}


//Reliable Broadcast

class EagerReliableBroadcast(init: Init[EagerReliableBroadcast]) extends ComponentDefinition {
  //EagerReliableBroadcast Subscriptions
  val beb = requires[BestEffortBroadcast];
  val rb = provides[ReliableBroadcast];

  //EagerReliableBroadcast Component State and Initialization
  val self = init match {
    case Init(s: Address) => s
  };
  val delivered = collection.mutable.Set[KompicsEvent]();

  //EagerReliableBroadcast Event Handlers
  rb uponEvent {
    case x@RB_Broadcast(payload) => {

      trigger(BEB_Broadcast(RB_Broadcast(payload)) -> beb);

    }
  }

  beb uponEvent {
    case BEB_Deliver(src, y@RB_Broadcast(payload)) => {

      if (!delivered.contains(payload)){

        delivered += payload;
        trigger(RB_Deliver(src, payload) -> rb);
        trigger(BEB_Broadcast(RB_Broadcast(payload)) -> beb);
      }

    }
  }
}



//Causal Reliable Broadcast

case class DataMessage(timestamp: VectorClock, payload: KompicsEvent) extends KompicsEvent;

class WaitingCRB(init: Init[WaitingCRB]) extends ComponentDefinition {

  //WaitingCRB Subscriptions
  val rb = requires[ReliableBroadcast];
  val crb = provides[CausalOrderReliableBroadcast];

  //WaitingCRB Component State and Initialization
  val (self, vec) = init match {
    case Init(s: Address, t: Set[Address]@unchecked) => (s, VectorClock.empty(t.toSeq))
  };

  //  val V = VectorClock.empty(init match { case Init(_, t: Set[Address]) => t.toSeq })
  var pending: ListBuffer[(Address, DataMessage)] = ListBuffer();
  var lsn = 0;


  //WaitingCRB Event Handlers
  crb uponEvent {
    case x: CRB_Broadcast => {

      var W = VectorClock(vec);
      W.set(self, lsn);
      lsn = lsn + 1;
      trigger(RB_Broadcast((DataMessage(W, x.payload))) -> rb);

    }
  }

  rb uponEvent {
    case x@RB_Deliver(src: Address, msg: DataMessage) => {

      pending += ((src, msg));
      while(pending.exists { p => p._2.timestamp<=vec }){
        for (p <- pending) {
          if (p._2.timestamp<=vec) {
            val (sender,dataMessage) = p;
            pending -= p;
            vec.inc(sender);     //incrreasing senders vector clock
            trigger(CRB_Deliver(sender,dataMessage.payload) -> crb);
          }
        }
      }

    }
  }
}
