// SPDX-License-Identifier: AGPL-3.0-or-later

/// play.sol -- Play Stablecoin ERC-20 Token

// Copyright (C) 2017, 2018, 2019 dbrock, rain, mrchico

// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.

pragma solidity ^0.6.12;

// FIXME: This contract was altered compared to the production version.
// It doesn't use LibNote anymore.
// New deployments of this contract will need to include custom events (TO DO).

contract Play {
    // --- Auth ---
    mapping (address => uint) public wards;
    function rely(address user) external auth { wards[user] = 1; }
    function deny(address user) external auth { wards[user] = 0; }
    modifier auth {
        require(wards[msg.sender] == 1, "Play/not-authorized");
        _;
    }

    // --- ERC20 Data ---
    string  public constant name     = "Play Token";
    string  public constant symbol   = "PLAY";
    string  public constant version  = "1";
    uint8   public constant decimals = 6;
    uint256 public totalSupply;

    mapping (address => uint)                      public balanceOf;
    mapping (address => mapping (address => uint)) public allowance;
    mapping (address => uint)                      public nonces;

    event Approval(address indexed src, address indexed user, uint amount);
    event Transfer(address indexed src, address indexed dst, uint amount);

    // --- Math ---
    function add(uint x, uint y) internal pure returns (uint z) {
        require((z = x + y) >= x);
    }
    function sub(uint x, uint y) internal pure returns (uint z) {
        require((z = x - y) <= x);
    }

    // --- EIP712 niceties ---
    bytes32 public DOMAIN_SEPARATOR;
    // bytes32 public constant PERMIT_TYPEHASH = keccak256("Permit(address holder,address spender,uint256 nonce,uint256 expiry,bool allowed)");
    bytes32 public constant PERMIT_TYPEHASH = 0xea2aa0a1be11a07ed86d755c93467f4f82362b452371d1ba94d1715123511acb;

    constructor(uint256 chainId_) public {
        wards[msg.sender] = 1;
        DOMAIN_SEPARATOR = keccak256(abi.encode(
            keccak256("EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)"),
            keccak256(bytes(name)),
            keccak256(bytes(version)),
            chainId_,
            address(this)
        ));
    }

    // --- Token ---
    function transfer(address dst, uint amount) external returns (bool) {
        return transferFrom(msg.sender, dst, amount);
    }
    function transferFrom(address src, address dst, uint amount)
        public returns (bool)
    {
        require(balanceOf[src] >= amount, "Play/insufficient-balance");
        if (src != msg.sender && allowance[src][msg.sender] != uint(-1)) {
            require(allowance[src][msg.sender] >= amount, "Play/insufficient-allowance");
            allowance[src][msg.sender] = sub(allowance[src][msg.sender], amount);
        }
        balanceOf[src] = sub(balanceOf[src], amount);
        balanceOf[dst] = add(balanceOf[dst], amount);
        emit Transfer(src, dst, amount);
        return true;
    }
    function mint(address usr, uint amount) external auth {
        balanceOf[usr] = add(balanceOf[usr], amount);
        totalSupply    = add(totalSupply, amount);
        emit Transfer(address(0), usr, amount);
    }
    function burn(address usr, uint amount) external {
        require(balanceOf[usr] >= amount, "Play/insufficient-balance");
        if (usr != msg.sender && allowance[usr][msg.sender] != uint(-1)) {
            require(allowance[usr][msg.sender] >= amount, "Play/insufficient-allowance");
            allowance[usr][msg.sender] = sub(allowance[usr][msg.sender], amount);
        }
        balanceOf[usr] = sub(balanceOf[usr], amount);
        totalSupply    = sub(totalSupply, amount);
        emit Transfer(usr, address(0), amount);
    }
    function approve(address usr, uint amount) external returns (bool) {
        allowance[msg.sender][usr] = amount;
        emit Approval(msg.sender, usr, amount);
        return true;
    }

    // --- Alias ---
    function push(address usr, uint amount) external {
        transferFrom(msg.sender, usr, amount);
    }
    function pull(address usr, uint amount) external {
        transferFrom(usr, msg.sender, amount);
    }
    function move(address src, address dst, uint amount) external {
        transferFrom(src, dst, amount);
    }

    // --- Approve by signature ---
    function permit(address holder, address spender, uint256 nonce, uint256 expiry,
                    bool allowed, uint8 v, bytes32 r, bytes32 s) external
    {
        bytes32 digest =
            keccak256(abi.encodePacked(
                "\x19\x01",
                DOMAIN_SEPARATOR,
                keccak256(abi.encode(PERMIT_TYPEHASH,
                                     holder,
                                     spender,
                                     nonce,
                                     expiry,
                                     allowed))
        ));

        require(holder != address(0), "Play/invalid-address-0");
        require(holder == ecrecover(digest, v, r, s), "Play/invalid-permit");
        require(expiry == 0 || now <= expiry, "Play/permit-expired");
        require(nonce == nonces[holder]++, "Play/invalid-nonce");
        uint amount = allowed ? uint(-1) : 0;
        allowance[holder][spender] = amount;
        emit Approval(holder, spender, amount);
    }
}