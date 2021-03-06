
class SequencePaxos(init: Init[SequencePaxos]) extends ComponentDefinition {

  import Role._
  import State._

  val sc = provides[SequenceConsensus];
  val ble = requires[BallotLeaderElection];
  val pl = requires[FIFOPerfectLink];

  val (self, pi, others) = init match {
    case Init(addr: Address, pi: Set[Address] @unchecked) => (addr, pi, pi - addr)
  }
  val majority = (pi.size / 2) + 1;

  var state = (FOLLOWER, UNKOWN);
  var nL = 0l;
  var nProm = 0l;
  var leader: Option[Address] = None;
  var na = 0l;
  var va = List.empty[RSM_Command];
  var ld = 0;
  // leader state
  var propCmds = List.empty[RSM_Command];
  val las = mutable.Map.empty[Address, Int];
  var lds = mutable.Map.empty[Address, Int];
  var lc = 0;
  var acks = mutable.Map.empty[Address, (Long, List[RSM_Command])];


  ble uponEvent {
    case BLE_Leader(l, n) => {
      /* INSERT YOUR CODE HERE */
      if(n>nL){
        leader=Option(l);
        nL=n;
        if(l==self && nL>nProm){
          state=(LEADER,PREPARE);
          propCmds = List.empty[RSM_Command];
          for (p <- pi) {
            las(p) = 0;
          }
          lds = mutable.Map.empty[Address, Int];
          acks = mutable.Map.empty[Address, (Long, List[RSM_Command])];
          lc=0;
          for(p<-others){
            trigger(PL_Send(p,Prepare(nL, ld, na))->pl);
          }
          acks(l)=(na, suffix(va,ld));
          lds(self)=ld;
          nProm=nL;

        }else{
          state=(FOLLOWER,state._2);
        }
      }
    }
  }

  pl uponEvent {
    case PL_Deliver(p, Prepare(np, ldp, n)) => {
      /* INSERT YOUR CODE HERE */
      if(nProm<np){
        nProm=np;
        state=(FOLLOWER,PREPARE);
        var sfx = List.empty[RSM_Command];
        if(na>=n){
          sfx=suffix(va, ld)
        }
        trigger(PL_Send(p,Promise(np, na, sfx,ld))->pl)
      }
    }
    case PL_Deliver(a, Promise(n, na, sfxa, lda)) => {
      if ((n == nL) && (state == (LEADER, PREPARE))) {
        /* INSERT YOUR CODE HERE */
        acks(a)=(na, sfxa);
        lds(a)=lda;

        if (acks.size >= majority) {
          var sfx = List.empty[RSM_Command];
          var maxRound = 0l;

          for (ack <- acks) {
            if ((ack._2._1 > maxRound)) {
              maxRound = ack._2._1;
              sfx = ack._2._2;
            }
          }

          va = prefix(va, ld) ++ sfx ++ propCmds;
          las(self) = va.size;
          propCmds = List.empty[RSM_Command];
          state = (LEADER, ACCEPT);
          for (p <- pi) {
            if (lds.contains(p) && p != self) {
              var sfxp = suffix(va, lds(p));
              trigger(PL_Send(p, AcceptSync(nL, sfxp, lds(p))) -> pl);
            }
          }
        }
      }
      else if ((n == nL) && (state == (LEADER, ACCEPT))) {
        /* INSERT YOUR CODE HERE */
        lds(a)=lda;
        var sfx=suffix(va, lds(a));
        trigger(PL_Send(a, AcceptSync(nL, sfx, lds(a))) -> pl);
        if(lc!=0){
          trigger(PL_Send(a, Decide(ld, nL))->pl)
        }
      }
    }
    case PL_Deliver(p, AcceptSync(nL, sfx, ldp)) => {
      if ((nProm == nL) && (state == (FOLLOWER, PREPARE))) {
        /* INSERT YOUR CODE HERE */
        if(nProm==nL){
          na=nL;
          va=prefix(va, ld)++sfx;
          trigger(PL_Send(p, Accepted(nL, va.size))->pl);
          state=(FOLLOWER, ACCEPT);
        }
      }
    }
    case PL_Deliver(p, Accept(nL, c)) => {
      if ((nProm == nL) && (state == (FOLLOWER, ACCEPT))) {
        /* INSERT YOUR CODE HERE */
        if(nProm==nL){
          va=va:::List(c);
          trigger(PL_Send(p, Accepted(nL,va.size))->pl);

        }
      }
    }
    case PL_Deliver(_, Decide(l, nL)) => {
      /* INSERT YOUR CODE HERE */
      if(nProm==nL){
        while(ld<l){
          trigger(SC_Decide(va(ld)) -> sc);
          ld=ld+1;
        }
      }
    }
    case PL_Deliver(a, Accepted(n, m)) => {
      if ((n == nL) && (state == (LEADER, ACCEPT))) {
        /* INSERT YOUR CODE HERE */
        las(a)=m;
        var P= Set.empty[Address];
        for(p<-pi){
          if(las(p)>=m){
            P=P+p;
          }
        }
        if (lc < m && P.size  >= majority) {
          lc = m;
          var nonEmptyLdsP= Set.empty[Address];
          for(q<-pi){
            if(lds.contains(q)){
              nonEmptyLdsP +=q;
            }
          }
          for (r <-nonEmptyLdsP ) {
            trigger(PL_Send(r, Decide(lc, nL)) -> pl);
          }
        }

      }
    }
  }

  sc uponEvent {
    case SC_Propose(c) => {
      if (state == (LEADER, PREPARE)) {
        /* INSERT YOUR CODE HERE */;
        propCmds=propCmds:::List(c);
      }
      else if (state==(LEADER,ACCEPT)){
        va=va ::: List(c);
        las(self)=las(self)+1;
        for(p<-others){
          if(lds.contains(p) && !p.equals(self)){
            trigger(PL_Send(p,Accept(nL, c))->pl);
          }
        }
      }
    }
  }
}