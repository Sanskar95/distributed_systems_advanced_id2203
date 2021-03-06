
class Paxos(paxosInit: Init[Paxos]) extends ComponentDefinition {

  //Port Subscriptions for Paxos

  val consensus = provides[Consensus];
  val beb = requires[BestEffortBroadcast];
  val plink = requires[PerfectLink];

  //Internal State of Paxos
  val (rank, numProcesses) = paxosInit match {
    case Init(s: Address, qSize: Int) => (toRank(s), qSize)
  }

  //Proposer State
  var round = 0;
  var proposedValue: Option[Any] = None;
  var promises: ListBuffer[((Int, Int), Option[Any])] = ListBuffer.empty;
  var numOfAccepts = 0;
  var decided = false;

  //Acceptor State
  var promisedBallot = (0, 0);
  var acceptedBallot = (0, 0);
  var acceptedValue: Option[Any] = None;

  def propose() = {
    /*
    INSERT YOUR CODE HERE
    */
    if(decided==false){
      round+=1;
      numOfAccepts=0;
      promises=ListBuffer.empty;
      trigger(BEB_Broadcast(Prepare((round, rank)))->beb);
    }
  }

  consensus uponEvent {
    case C_Propose(value) => {
      /*
      INSERT YOUR CODE HERE
      */
      proposedValue = Some(value);
      propose();
    }
  }


  beb uponEvent {

    case BEB_Deliver(src, prep: Prepare) => {
      /*
      INSERT YOUR CODE HERE
      */
      if(promisedBallot<prep.proposalBallot){
        promisedBallot=prep.proposalBallot;
        trigger(PL_Send(src,Promise(promisedBallot, acceptedBallot, acceptedValue))->plink);
      }else
      {
        trigger(PL_Send(src,Nack(prep.proposalBallot))->plink);
      }
    };

    case BEB_Deliver(src, acc: Accept) => {
      /*
      INSERT YOUR CODE HERE
      */
      if(promisedBallot<=acc.acceptBallot){
        promisedBallot=acc.acceptBallot;
        acceptedBallot=acc.acceptBallot;
        acceptedValue=Some(acc.proposedValue);
        trigger(PL_Send(src,Accepted(acc.acceptBallot))->plink);
      }else
      {
        trigger(PL_Send(src,Nack(acc.acceptBallot))->plink);
      }
    };

    case BEB_Deliver(src, dec : Decided) => {
      /*
      INSERT YOUR CODE HERE
      */
      if(decided==false){
        trigger(C_Decide(dec.decidedValue)->consensus);
        decided=true;
      }
    }
  }

  plink uponEvent {

    case PL_Deliver(src, prepAck: Promise) => {
      if ((round, rank) == prepAck.promiseBallot) {
        /*
           INSERT YOUR CODE HERE
        */
        promises+=((prepAck.acceptedBallot,prepAck.acceptedValue));
        if(promises.length == (1+(numProcesses)/2)){
          println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&",promises);
          val (maxBallot, value) =promises.maxBy { case (key, value) => key };
          if(value!=None){
            proposedValue=value;
          }
          //  proposedValue=Some(value.getOrElse(proposedValue.get));

          trigger(BEB_Broadcast(Accept((round, rank), proposedValue.get))->beb);
        }
      }
    };

    case PL_Deliver(src, accAck: Accepted) => {
      if ((round, rank) == accAck.acceptedBallot) {
        /*
           INSERT YOUR CODE HERE
        */
        numOfAccepts = numOfAccepts +1;
        if(numOfAccepts==(1+(numProcesses)/2)){
          trigger(BEB_Broadcast(Decided(proposedValue.get))->beb);
        }
      }
    };

    case PL_Deliver(src, nack: Nack) => {
      if ((round, rank) == nack.ballot) {
        /*
           INSERT YOUR CODE HERE
        */
        propose();
      }
    }
  }


};