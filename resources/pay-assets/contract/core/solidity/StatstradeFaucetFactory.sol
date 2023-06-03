// SPDX-License-Identifier: GPL-3.0
pragma solidity >=0.7.0 <0.9.0;

interface IERC20 {
  function transfer(address to,uint value) external returns(bool);
  function balanceOf(address owner) external view returns(uint);
  function transferFrom(address from,address receiver,uint amount) external ;
}

contract StatstradeFaucetFactory {
  enum FaucetStatus { Undefined, Active }
  
  address public g__SiteAuthority;
  
  mapping(address => bool) public g__SiteSupport;
  
  mapping(string => mapping(address => uint)) public g__SiteRequests;
  
  struct Faucet{
    FaucetStatus status;
    string name;
    address token_address;
    uint limit_amount;
    uint limit_frequency;
    uint limit_balance;
  }
  
  mapping(string => Faucet) public g__FaucetLookup;
  
  constructor() {
    g__SiteAuthority = msg.sender;
  }
  
  event FaucetEvent(string event_type,string event_id,address sender,uint value);
  
  function ut__assert_admin(address user_address) internal view {
    require(
      (user_address == g__SiteAuthority) || g__SiteSupport[user_address],
      "Site admin only."
    );
  }
  
  function site__change_authority(address new_authority) external {
    require(msg.sender == g__SiteAuthority,"Site authority only.");
    g__SiteAuthority = new_authority;
  }
  
  function site__add_support(address user) external {
    require(msg.sender == g__SiteAuthority,"Site authority only.");
    g__SiteSupport[user] = true;
  }
  
  function site__remove_support(address user) external {
    ut__assert_admin(msg.sender);
    delete g__SiteSupport[user];
  }
  
  function site__create_faucet(string memory faucet_id,string memory name,address token_address,uint limit_amount,uint limit_frequency,uint limit_balance) external {
    ut__assert_admin(msg.sender);
    require(
      FaucetStatus.Undefined == g__FaucetLookup[faucet_id].status,
      "Faucet already exists."
    );
    Faucet memory faucet = Faucet({
      status: FaucetStatus.Active,
      name: name,
      token_address: token_address,
      limit_amount: limit_amount,
      limit_frequency: limit_frequency,
      limit_balance: limit_balance
    });
    g__FaucetLookup[faucet_id] = faucet;
    emit FaucetEvent("faucet_create",faucet_id,token_address,0);
  }
  
  function site__remove_faucet(string memory faucet_id,uint limit) external {
    ut__assert_admin(msg.sender);
    delete g__FaucetLookup[faucet_id];
  }
  
  function site__limit_amount(string memory faucet_id,uint limit) external {
    ut__assert_admin(msg.sender);
    require(
      (g__FaucetLookup[faucet_id].status == FaucetStatus.Active),
      "Faucet not found"
    );
    g__FaucetLookup[faucet_id].limit_amount == limit;
  }
  
  function site__limit_frequency(string memory faucet_id,uint limit) external {
    ut__assert_admin(msg.sender);
    require(
      (g__FaucetLookup[faucet_id].status == FaucetStatus.Active),
      "Faucet not found"
    );
    g__FaucetLookup[faucet_id].limit_frequency == limit;
  }
  
  function site__limit_balance(string memory faucet_id,uint limit) external {
    ut__assert_admin(msg.sender);
    require(
      (g__FaucetLookup[faucet_id].status == FaucetStatus.Active),
      "Faucet not found"
    );
    g__FaucetLookup[faucet_id].limit_balance == limit;
  }
  
  function user__request_balance_faucet(string memory faucet_id) external view returns(uint) {
    require(
      (g__FaucetLookup[faucet_id].status == FaucetStatus.Active),
      "Faucet not found"
    );
    address token_address = g__FaucetLookup[faucet_id].token_address;
    IERC20 erc20 = IERC20(token_address);
    return erc20.balanceOf(address(this));
  }
  
  function user__request_balance_caller(string memory faucet_id) external view returns(uint) {
    require(
      (g__FaucetLookup[faucet_id].status == FaucetStatus.Active),
      "Faucet not found"
    );
    address token_address = g__FaucetLookup[faucet_id].token_address;
    IERC20 erc20 = IERC20(token_address);
    return erc20.balanceOf(address(msg.sender));
  }
  
  function user__request_topup(string memory faucet_id,uint amount) external {
    Faucet memory faucet = g__FaucetLookup[faucet_id];
    require((faucet.status == FaucetStatus.Active),"Faucet not found");
    uint prev = g__SiteRequests[faucet_id][msg.sender];
    require(
      (0 == faucet.limit_frequency) || ((prev + faucet.limit_frequency) < block.timestamp),
      "Request frequency over limit"
    );
    require(
      (0 == faucet.limit_amount) || (amount < faucet.limit_amount),
      "Request amount over limit"
    );
    IERC20 erc20 = IERC20(faucet.token_address);
    uint balance = erc20.balanceOf(msg.sender);
    require(
      (0 == faucet.limit_balance) || ((balance + amount) <= faucet.limit_balance),
      "Request balance over limit"
    );
    erc20.transfer(msg.sender,amount);
    g__SiteRequests[faucet_id][msg.sender] = block.timestamp;
    emit FaucetEvent("faucet_topup",faucet_id,msg.sender,amount);
  }
  
  function user__request_test_transfer(address token_address,address user_address) external {
    IERC20 erc20 = IERC20(token_address);
    erc20.transfer(user_address,1);
  }
}