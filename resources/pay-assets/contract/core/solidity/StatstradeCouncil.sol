// SPDX-License-Identifier: GPL-3.0
pragma solidity >=0.7.0 <0.9.0;

interface IERC20 {
  function transfer(address to,uint value) external returns(bool);
  function balanceOf(address owner) external view returns(uint);
}

contract StatstradeCouncil {
  enum VoteType { Accept, Reject, Abstain }
  
  enum ProposalType { MemberTakeProfit, MemberAdd, MemberRemove }
  
  enum ProposalStatus { Undefined, Pending, Cancelled, Accepted, Rejected }
  
  struct Proposal{
    ProposalStatus status;
    ProposalType proposal_type;
    address value;
    uint8 total_yes;
    uint8 total_no;
    uint8 total_abstained;
    uint8 total_voting;
  }
  
  uint8 public g__MemberQuorum;
  
  address [] public g__MemberArray;
  
  mapping(address => string) public g__ProposalActive;
  
  mapping(string => Proposal) public g__ProposalLookup;
  
  mapping(string => mapping(address => bool)) public g__ProposalVoting;
  
  event CouncilEvent(string event_type,string event_id,address event_address,uint event_value);
  
  constructor(uint8 quorum) {
    g__MemberArray.push(msg.sender);
    g__MemberQuorum = quorum;
  }
  
  function ut__get_quorum(string memory proposal_id) internal view returns(bool) {
    Proposal memory proposal = g__ProposalLookup[proposal_id];
    if(proposal.total_voting == proposal.total_abstained){
      return false;
    }
    uint total = (proposal.total_voting - proposal.total_abstained);
    if(((proposal.total_yes * 100) / total) > g__MemberQuorum){
      return true;
    }
    else if(((proposal.total_no * 100) / total) > g__MemberQuorum){
      return false;
    }
    else{
      revert("No Quorum.");
    }
  }
  
  function ut__get_index(address user_address) internal view returns(uint) {
    if(g__MemberArray.length == 0){
      return 0;
    }
    for(uint i = 0; uint(i) < g__MemberArray.length; i = (i + 1)){
      if(g__MemberArray[i] == user_address){
        return i + 1;
      }
    }
    return 0;
  }
  
  function ut__get_remove(address user_address) internal returns(bool) {
    uint i = ut__get_index(user_address);
    if(i == 0){
      return false;
    }
    uint last_idx = (g__MemberArray.length - 1);
    g__MemberArray[i - 1] == g__MemberArray[last_idx];
    g__MemberArray.pop();
    return true;
  }
  
  function ut__get_add(address user_address) internal returns(bool) {
    uint i = ut__get_index(user_address);
    if(i == 0){
      g__MemberArray.push(user_address);
      return true;
    }
    else{
      return false;
    }
  }
  
  function action__member_take_profit(string memory proposal_id,address erc20_address) internal {
    IERC20 erc20 = IERC20(erc20_address);
    uint total = erc20.balanceOf(address(this));
    uint part = (total / g__MemberArray.length);
    for(uint i = 0; i < g__MemberArray.length; i = (i + 1)){
      erc20.transfer(g__MemberArray[i],part);
    }
    emit CouncilEvent("member_take_profit",proposal_id,erc20_address,total);
  }
  
  function action__member_add(string memory proposal_id,address user_address) internal {
    if(ut__get_add(user_address)){
      emit CouncilEvent("member_add",proposal_id,user_address,g__MemberArray.length);
    }
  }
  
  function action__member_remove(string memory proposal_id,address user_address) internal {
    if(ut__get_remove(user_address)){
      emit CouncilEvent("member_remove",proposal_id,user_address,g__MemberArray.length);
    }
  }
  
  function member__proposal_create(string memory proposal_id,ProposalType proposal_type,address value) external {
    require(0 < ut__get_index(msg.sender),"Member only.");
    require(
      ProposalStatus.Pending != g__ProposalLookup[g__ProposalActive[msg.sender]].status,
      "Proposal pending."
    );
    require(
      ProposalStatus.Undefined == g__ProposalLookup[proposal_id].status,
      "Proposal exists."
    );
    Proposal memory proposal = Proposal({
      status: ProposalStatus.Pending,
      proposal_type: proposal_type,
      value: value,
      total_yes: 1,
      total_no: 0,
      total_abstained: 0,
      total_voting: uint8(g__MemberArray.length)
    });
    g__ProposalLookup[proposal_id] = proposal;
    g__ProposalActive[msg.sender] = proposal_id;
    g__ProposalVoting[proposal_id][msg.sender] = true;
  }
  
  function member__proposal_vote(string memory proposal_id,VoteType vote_type) external {
    require(0 < ut__get_index(msg.sender),"Member only.");
    require(
      ProposalStatus.Pending == g__ProposalLookup[proposal_id].status,
      "Invalid status."
    );
    require(!g__ProposalVoting[proposal_id][msg.sender]);
    if(vote_type == VoteType.Accept){
      ++g__ProposalLookup[proposal_id].total_yes;
    }
    else if(vote_type == VoteType.Reject){
      ++g__ProposalLookup[proposal_id].total_no;
    }
    else if(vote_type == VoteType.Abstain){
      ++g__ProposalLookup[proposal_id].total_abstained;
    }
    g__ProposalVoting[proposal_id][msg.sender] = true;
  }
  
  function member__proposal_cancel(string memory proposal_id) external {
    require(
      ProposalStatus.Pending == g__ProposalLookup[g__ProposalActive[msg.sender]].status,
      "Not pending."
    );
    require(
      ProposalStatus.Pending == g__ProposalLookup[proposal_id].status,
      "Invalid status."
    );
    g__ProposalLookup[proposal_id].status = ProposalStatus.Cancelled;
    delete g__ProposalActive[msg.sender];
  }
  
  function member__proposal_process(string memory proposal_id) external {
    require(
      ProposalStatus.Pending == g__ProposalLookup[g__ProposalActive[msg.sender]].status,
      "Not pending."
    );
    require(
      ProposalStatus.Pending == g__ProposalLookup[proposal_id].status,
      "Invalid status."
    );
    bool result = ut__get_quorum(proposal_id);
    g__ProposalLookup[proposal_id].status = (result ? ProposalStatus.Accepted : ProposalStatus.Rejected);
    delete g__ProposalActive[msg.sender];
    if(result){
      ProposalType proposal_type = g__ProposalLookup[proposal_id].proposal_type;
      address event_address = g__ProposalLookup[proposal_id].value;
      if(proposal_type == ProposalType.MemberAdd){
        action__member_add(proposal_id,event_address);
      }
      else if(proposal_type == ProposalType.MemberRemove){
        action__member_remove(proposal_id,event_address);
      }
      else if(proposal_type == ProposalType.MemberTakeProfit){
        action__member_take_profit(proposal_id,event_address);
      }
    }
  }
}