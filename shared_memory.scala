
class ReadImposeWriteConsultMajority(init: Init[ReadImposeWriteConsultMajority]) extends ComponentDefinition {

  //subscriptions

  val nnar = provides[AtomicRegister];

  val pLink = requires[PerfectLink];
  val beb = requires[BestEffortBroadcast];

  //state and initialization

  val (self: Address, n: Int, selfRank: Int) = init match {
    case Init(selfAddr: Address, n: Int) => (selfAddr, n, AddressUtils.toRank(selfAddr))
  };

  var (ts, wr) = (0, 0);
  var value: Option[Any] = None;
  var acks = 0;
  var readval: Option[Any] = None;
  var writeval: Option[Any] = None;
  var rid = 0;
  var readlist: Map[Address, (Int, Int, Option[Any])] = Map.empty
  var reading = false;

  //handlers

  nnar uponEvent {
    case AR_Read_Request() => {
      rid = rid + 1;
      /* WRITE YOUR CODE HERE  */
      acks=0;
      readlist= Map.empty;
      reading = true;
      trigger(BEB_Broadcast(READ(rid))-> beb);

    };
    case AR_Write_Request(wval) => {
      rid = rid + 1;
      writeval= Some(wval);
      acks=0;
      readlist= Map.empty;
      trigger(BEB_Broadcast(READ(rid))-> beb);

      /* WRITE YOUR CODE HERE  */

    }
  }

  beb uponEvent {
    case BEB_Deliver(src, READ(readID)) => {

      /* WRITE YOUR CODE HERE  */
      trigger(PL_Send(src,VALUE(readID, ts, wr, value))->pLink);

    }
    case BEB_Deliver(src, w: WRITE) => {

      /* WRITE YOUR CODE HERE  */
      //  var (ts', wr')
      if((w.ts,w.wr)>(ts,wr)){
        ts=w.ts;
        wr=w.wr;
        value=w.writeVal;
      }
      trigger(PL_Send(src, ACK(w.rid))->pLink);
    }
  }

  pLink uponEvent {
    case PL_Deliver(src, v: VALUE) => {
      if (v.rid == rid) {
        /* WRITE YOUR CODE HERE  */
        readlist(src)=(v.ts, v.wr, v.value);
        //   readlist.update(src,(v.ts, v.wr, v.value));
        if(readlist.size > n/2){
          var bcastval=None: Option[Any];
          var maxts = 0;
          var rr= 0;


          for ((k,vprime) <- readlist){
            if((vprime._1,vprime._2) > (maxts, rr)){
              maxts=vprime._1;
              rr = vprime._2;
              readval= vprime._3;
            }
            //   if(vprime._2> rr){

            //   }
            // //   if(vprime._3.get.asInstanceOf[Int] > readval.get.asInstanceOf[Int]){
            // //       readval = vprime._3;
            // //   }

          }


          readval = readlist.maxBy(maplist => (maplist._2._1, maplist._2._2))._2._3;

          println(maxts);
          println(rr);
          println(readval);
          readlist= Map.empty;
          if (reading==true){
            bcastval= readval;
          }else{
            rr=selfRank;
            maxts= maxts+1;
            bcastval=writeval;
          }
          trigger(BEB_Broadcast(WRITE(rid,maxts,rr,bcastval))->beb);
        } //shady, might die


      }
    }
    case PL_Deliver(src, v: ACK) => {
      if (v.rid == rid) {

        /* WRITE YOUR CODE HERE  */
        acks= acks+1;
        if(acks>n/2){
          acks=0;
          if(reading==true){
            reading=false;
            trigger(AR_Read_Response(readval)->nnar);
          }else{
            trigger(AR_Write_Response()->nnar);

          }
        }

      }
    }
  }
}