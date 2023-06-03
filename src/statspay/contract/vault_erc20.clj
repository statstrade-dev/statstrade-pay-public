(ns statspay.contract.vault-erc20
  (:require [std.lang :as l]
            [std.lib :as h]
			[rt.solidity :as s]))

(l/script :solidity
  {:require [[rt.solidity :as s]]})

(definterface.sol IERC20
  [^{:- [:external]
     :static/returns :bool}
   transfer [:address to
             :uint value]
   ^{:- [:external :view]
     :static/returns :uint}
   balanceOf [:address owner]
   ^{:- [:external]}
   transferFrom [:address from
                 :address receiver
                 :uint amount]])

(defenum.sol VaultStatus
  [:Undefined :Active :ActiveAuto :Arbitration])

(defenum.sol VaultAccountStatus
  [:Undefined :Requested :Active :Locked])

(defenum.sol VaultRequestStatus
  [:Undefined :Pending :Confirmed :RejectedSite :Rejected :Approved])

;;
;; GLOBALS
;;

(defaddress.sol ^{:- [:public]}
  g:SiteAuthority)

(defmapping.sol ^{:- [:public]}
  g:SiteSupport
  [:address :bool])

(def.sol ^{:- [:uint16 :public]}
  g:SiteArbitrationThreshold)

(defstruct.sol WithdrawRequest
  [:uint    amount]
  [:% -/VaultRequestStatus status])

(defstruct.sol Vault
  [:% -/VaultStatus status]
  [:string      name]
  [:address     owner]
  [:uint        total-pool]
  [:uint        total-requested]
  [:uint16      total-players]
  [:uint16      total-arbitration]
  [:uint16      vault-tax-rate]
  [:uint        vault-tax-max]
  [:uint        vault-withdraw-min]
  [:uint        vault-withdraw-max]
  [:address     vault-erc20])

(defstruct.sol VaultAccount
  [:% -/VaultAccountStatus status]
  [:bool        arbitration]
  [:string      last-withdraw-id]
  [:uint        last-withdraw-time])

(defevent.sol VaultEvent
  [:string     event-type]
  [:string     event-id]
  [:address    sender]
  [:uint       value])


;;
;; GLOBALS
;;

(def.sol ^{:- [:uint16 :public]}
  g:VaultCount)

(defmapping.sol ^{:- [:public]}
  g:VaultLookup
  [:string  -/Vault])

(defmapping.sol ^{:- [:public]}
  g:VaultAccounts
  [:string  (:mapping [:address -/VaultAccount])])

(defmapping.sol ^{:- [:public]}
  g:VaultWithdrawals
  [:string  (:mapping [:address (:mapping [:string -/WithdrawRequest])])])

;;
;; CONSTRUCTOR
;;

(defconstructor.sol
  __init__
  [:uint16 site-arbitration-threshold]
  (:= -/g:SiteAuthority s/msg-sender)
  (:= -/g:SiteArbitrationThreshold site-arbitration-threshold))

;;
;; INTERNAL
;;

(defn.sol ^{:- [:internal :view]}
  ut:assert-admin
  "asserts that address is admin"
  {:added "0.1"}
  [:address user-address]
  (s/require (or (== user-address -/g:SiteAuthority)
                 (. -/g:SiteSupport [user-address]))
             "Site admin only."))

(defn.sol ^{:- [:internal :view]}
  ut:assert-owner
  "asserts that address is vault owner"
  {:added "0.1"}
  [:string :memory vault-id
   :address user-address]
  (s/require (== (. -/g:VaultLookup [vault-id] owner)
                 user-address)
             "Vault owner only.")
  (s/require (not= (. -/g:VaultLookup [vault-id] status)
                   (. -/VaultStatus Arbitration))
             "Vault in arbitration."))

(defn.sol ^{:- [:internal :view]}
  ut:assert-management
  "asserts that address is vault owner or site account"
  {:added "0.1"}
  [:string :memory vault-id
   :address user-address]
  (s/require (or (== (. -/g:VaultLookup [vault-id] owner)
                     user-address)
                 (== user-address -/g:SiteAuthority)
                 (. -/g:SiteSupport [user-address]))
             "Vault management only."))

(defn.sol ^{:- [:internal :view]
            :static/returns [statspay.contract.vault-erc20/VaultAccount :memory]}
  ut:get-account
  "gets an account in the vault"
  {:added "0.1"}
  [:string :memory vault-id
   :address user-address]
  (var (-/VaultAccount :memory account)
       (. -/g:VaultAccounts [vault-id] [user-address]))
  (s/require (not= (. account status)
                   (. -/VaultAccountStatus Undefined))
             "User not found.")
  (return account))

(defn.sol ^{:- [:internal :view]
            :static/returns [statspay.contract.vault-erc20/WithdrawRequest :memory]}
  ut:get-withdraw
  "gets a withdraw in the vault"
  {:added "0.1"}
  [:string :memory vault-id
   :address user-address
   :string :memory withdraw-id]
  (var (-/WithdrawRequest :memory request)
       (. -/g:VaultWithdrawals [vault-id] [user-address] [withdraw-id]))
  (s/require (not= (. request status)
                   (. -/VaultRequestStatus Undefined))
             "Request not found.")
  (return request))

;;
;;
;;

(defn.sol ^{:- [:external]}
  site:change-authority
  "link account"
  {:added "0.1"}
  [:address new-authority]
  (require (== s/msg-sender -/g:SiteAuthority)
           "Site authority only.")
  (:= -/g:SiteAuthority new-authority))

(defn.sol ^{:- [:external]}
  site:add-support
  "adds supporting keys to the contract"
  {:added "4.0"}
  [:address user]
  (s/require (== s/msg-sender -/g:SiteAuthority)
             "Site authority only.")
  (:= (. -/g:SiteSupport [user]) true))

(defn.sol ^{:- [:external]}
  site:remove-support
  "removes supporting keys from the contract"
  {:added "4.0"}
  [:address user]
  (-/ut:assert-admin s/msg-sender)
  (delete (. -/g:SiteSupport [user])))

(defn.sol ^{:- [:external]}
  site:vault-create
  "creates a vault"
  {:added "0.1"}
  [:string :memory vault-id
   :string :memory name
   :address owner
   :uint16  vault-tax-rate
   :uint    vault-tax-max
   :uint    vault-withdraw-min
   :uint    vault-withdraw-max
   :address vault-erc20]
  (-/ut:assert-admin s/msg-sender)
  (s/require (== (. -/VaultStatus Undefined)
                 (. -/g:VaultLookup [vault-id] status))
             "Vault already exists.")
  (var (-/Vault :memory vault)
       (-/Vault
        {:status (. -/VaultStatus Active)
         :name  name
         :owner owner
         :total-pool 0
         :total-requested 0
         :total-players 0
         :total-arbitration 0
         :vault-tax-rate   vault-tax-rate
         :vault-tax-max    vault-tax-max
         :vault-withdraw-min vault-withdraw-min
         :vault-withdraw-max vault-withdraw-max
         :vault-erc20      vault-erc20}))
  (:++ -/g:VaultCount)
  (:= (. -/g:VaultLookup [vault-id])
      vault)
  (emit (-/VaultEvent "vault_create"
                     vault-id
                     owner
                     0)))

(defn.sol ^{:- [:external]}
  site:account-open
  "opens an account in a vault"
  {:added "0.1"}
  [:string :memory vault-id
   :address user-address]
  (-/ut:assert-admin s/msg-sender)
  (s/require (or (== (. -/g:VaultLookup [vault-id] status)
                     (. -/VaultStatus Active))
                 (== (. -/g:VaultLookup [vault-id] status)
                     (. -/VaultStatus ActiveAuto)))
             "Vault incorrect status")
  (s/require (== (. -/g:VaultAccounts [vault-id] [user-address] status)
                 (. -/VaultAccountStatus Undefined))
             "Account already registered.")
  (var (-/VaultAccount :memory account)
       (-/VaultAccount
        (. -/VaultAccountStatus Requested)
        false
        ""
        0))
  (var (:uint16 total-players) (:++  (. -/g:VaultLookup [vault-id] total-players)))
  (:= (. -/g:VaultAccounts [vault-id] [user-address]) account)
  (emit (-/VaultEvent "account_open"
                     vault-id
                     user-address
                     total-players)))

(defn.sol ^{:- [:external]}
  site:account-close
  "closes an account in a vault"
  {:added "0.1"}
  [:string :memory vault-id
   :address user-address]
  (-/ut:assert-admin s/msg-sender)
  (var (-/VaultAccount :memory account) (-/ut:get-account vault-id user-address))
  (s/require (or (== (. account status)
                     (. -/VaultAccountStatus Active))
                 (== (. account status)
                     (. -/VaultAccountStatus Requested)))
             "Account incorrect status.")
  (delete (. -/g:VaultAccounts [vault-id] [user-address]))
  (var (:uint16 total-players) (:--  (. -/g:VaultLookup [vault-id] total-players)))
  (emit (-/VaultEvent "account_close"
                     vault-id
                     user-address
                     total-players)))

(defn.sol ^{:- [:external]}
  owner:set-auto
  "locks the current account"
  {:added "0.1"}
  [:string :memory vault-id
   :bool is-auto]
  (-/ut:assert-owner vault-id s/msg-sender)
  (s/require (not= (. -/g:VaultLookup [vault-id] status)
                   (. -/VaultStatus Arbitration))
             "Vault in Arbitration")
  (:= (. -/g:VaultLookup [vault-id] status)
      (:? is-auto
          (. -/VaultStatus ActiveAuto)
          (. -/VaultStatus Active))))

(defn.sol ^{:- [:external]}
  owner:account-open-confirm
  "locks the current account"
  {:added "0.1"}
  [:string :memory vault-id
   :address user-address]
  (-/ut:assert-owner vault-id s/msg-sender)
  (var (-/VaultAccount :memory account) (-/ut:get-account vault-id user-address))
  (s/require (== (. account status)
                 (. -/VaultAccountStatus Requested))
             "Account incorrect status.")
  (:= (. -/g:VaultAccounts [vault-id] [user-address] status)
      (. -/VaultAccountStatus Active))
  (emit (-/VaultEvent "account_open_confirm"
                      vault-id
                     user-address
                     0)))

(defn.sol ^{:- [:external]}
  owner:account-lock
  "locks the current account"
  {:added "0.1"}
  [:string :memory vault-id
   :address user-address]
  (-/ut:assert-management vault-id s/msg-sender)
  (var (-/VaultAccount :memory account) (-/ut:get-account vault-id user-address))
  (s/require (== (. account status)
                 (. -/VaultAccountStatus Active))
             "Account incorrect status.")
  (:= (. -/g:VaultAccounts [vault-id] [user-address] status)
      (. -/VaultAccountStatus Locked))
  (emit (-/VaultEvent "account_lock"
                     vault-id
                     user-address
                     0)))

(defn.sol ^{:- [:external]}
  owner:account-unlock
  "unlocks the vault"
  {:added "0.1"}
  [:string :memory vault-id
   :address user-address]
  (-/ut:assert-owner vault-id s/msg-sender)
  (var (-/VaultAccount :memory account) (-/ut:get-account vault-id user-address))
  (s/require (== (. account status)
                 (. -/VaultAccountStatus Locked))
             "Account not banned.")
  (:= (. -/g:VaultAccounts [vault-id] [user-address] status)
      (. -/VaultAccountStatus Active))
  (emit (-/VaultEvent "account_unlock"
                     vault-id
                     user-address
                     0)))

(defn.sol ^{:- [:external]}
  user:arbitration-vote
  "creates an arbitration vote"
  {:added "0.1"}
  [:string :memory vault-id]
  (var (-/VaultAccount :memory account) (-/ut:get-account vault-id s/msg-sender))
  (s/require (not (. account arbitration))
             "Account arbitration voted.")
  (:= (. -/g:VaultAccounts [vault-id] [s/msg-sender] arbitration) true)
  (var (:uint16 total-arbitration) (:++ (. -/g:VaultLookup [vault-id] total-arbitration)))
  (var (:uint16 total-players) (. -/g:VaultLookup [vault-id] total-players))
  (when (and (> total-arbitration 4)
             (> total-arbitration
                (/ (* -/g:SiteArbitrationThreshold total-players)
                   100)))
    (:= (. -/g:VaultLookup [vault-id] status)
        (. -/VaultStatus Arbitration)))
  (emit (-/VaultEvent "arbitration_vote"
                     vault-id
                     s/msg-sender
                     total-arbitration)))

(defn.sol ^{:- [:external]}
  user:arbitration-unvote
  "unvotes on site arbitration"
  {:added "0.1"}
  [:string :memory vault-id]
  (s/require (not= (. -/g:VaultLookup [vault-id] status)
                   (. -/VaultStatus Arbitration))
             "Vault in arbitration.")
  (var (-/VaultAccount :memory account) (-/ut:get-account vault-id s/msg-sender))
  (s/require (. account arbitration)
             "Account not voted.")
  (delete (. -/g:VaultAccounts [vault-id] [s/msg-sender] arbitration))
  (var (:uint16 total-arbitration) (:-- (. -/g:VaultLookup [vault-id] total-arbitration)))
  (emit (-/VaultEvent "arbitration_unvote"
                     vault-id
                     s/msg-sender
                     total-arbitration)))

(defn.sol ^{:- [:external]}
  user:withdraw-request
  "requests a withdraw"
  {:added "0.1"}
  [:string :memory vault-id
   :string :memory withdraw-id
   :uint amount]
  (var (-/VaultAccount :memory account) (. -/g:VaultAccounts [vault-id] [s/msg-sender]))
  (var (-/Vault :memory vault) (. -/g:VaultLookup [vault-id]))
  (s/require (or (== (. account status)
                     (. -/VaultAccountStatus Active))
                 (== (. vault status)
                     (. -/VaultStatus Arbitration)))
             "Withdraw not allowed.")
  
  (s/require (== (. -/g:VaultWithdrawals [vault-id] [s/msg-sender] [withdraw-id] status)
                 (. -/VaultRequestStatus Undefined))
             "Withdraw already requested.")
  
  (s/require (>= amount (. vault vault-withdraw-min))
             "Withdraw amount below minimum.")
  (s/require (<= amount (. vault vault-withdraw-max))
             "Withdraw amount above maximum")
  (var (:uint new-total-requested) (+ (. -/g:VaultLookup [vault-id] total-requested) amount))
  
  (s/require (>= (. -/g:VaultLookup [vault-id] total-pool) 
                 new-total-requested)
             "Withdraw over pool limit.")
  (:=  (. -/g:VaultWithdrawals [vault-id] [s/msg-sender] [withdraw-id])
       (-/WithdrawRequest amount (. -/VaultRequestStatus Pending)))
  (:= (. -/g:VaultLookup [vault-id] total-requested)
      new-total-requested)
  (emit (-/VaultEvent "withdraw_request"
                     withdraw-id
                     s/msg-sender
                     amount)))

(defn.sol ^{:- [:external]}
  site:withdraw-confirm
  "confirms a withdraw"
  {:added "0.1"}
  [:string :memory vault-id
   :address user-address
   :string :memory withdraw-id
   :uint amount]
  (-/ut:assert-admin s/msg-sender)
  (var (-/WithdrawRequest :memory request) (-/ut:get-withdraw vault-id user-address withdraw-id))
  (s/require (== (. request status)
                 (. -/VaultRequestStatus Pending))
             "Withdraw not pending")
  (s/require (== (. request amount) amount)
             "Withdraw amount incorrect")
  
  (:= (. -/g:VaultWithdrawals [vault-id] [user-address] [withdraw-id] status)
      (. -/VaultRequestStatus Confirmed))
  (emit (-/VaultEvent "withdraw_confirm_site"
                     withdraw-id
                     user-address
                     amount)))

(defn.sol ^{:- [:external]}
  site:withdraw-reject
  "confirms a withdraw"
  {:added "0.1"}
  [:string :memory vault-id
   :address user-address
   :string :memory withdraw-id]
  (-/ut:assert-admin s/msg-sender)
  (var (-/WithdrawRequest :memory request) (-/ut:get-withdraw vault-id user-address withdraw-id))
  (s/require (== (. request status)
                 (. -/VaultRequestStatus Pending))
             "Withdraw not pending")
  (:= (. -/g:VaultWithdrawals [vault-id] [user-address] [withdraw-id] status)
      (. -/VaultRequestStatus RejectedSite))
  (:-= (. -/g:VaultLookup [vault-id] total-requested) (. request amount))
  (emit (-/VaultEvent "withdraw_reject_site"
                     withdraw-id
                     user-address
                     (. request amount))))

(defn.sol ^{:- [:internal]}
  ut:withdraw-transfer
  "helper function to facilitate transfer"
  {:added "0.1"}
  [:string :memory vault-id
   :address user-address
   :string :memory withdraw-id
   :% -/WithdrawRequest :memory request
   :bool arbitrated]
  (var (:uint vault-tax) (/ (* (. request amount)
                               (. -/g:VaultLookup [vault-id] vault-tax-rate))
                           10000))
  (when (> vault-tax (. -/g:VaultLookup [vault-id] vault-tax-max))
    (:= vault-tax (. -/g:VaultLookup [vault-id] vault-tax-max)))
  
  (var (-/IERC20 erc20) (-/IERC20 (. -/g:VaultLookup [vault-id] vault-erc20)))

  
  (cond arbitrated
        (. erc20 (transfer -/g:SiteAuthority
                           vault-tax))
        
        :else
        (. erc20 (transfer (. -/g:VaultLookup [vault-id] owner)
                           vault-tax)))
  (. erc20 (transfer user-address
                     (- (. request amount)
                        vault-tax)))

  (:= (. -/g:VaultWithdrawals [vault-id] [user-address] [withdraw-id] status)
      (. -/VaultRequestStatus Approved))
  (:-= (. -/g:VaultLookup [vault-id] total-pool) (. request amount))
  (:-= (. -/g:VaultLookup [vault-id] total-requested) (. request amount))
  (emit (-/VaultEvent "withdraw_transfer"
                     withdraw-id
                     user-address
                     (. request amount))))

(defn.sol ^{:- [:external]}
  owner:withdraw-approve
  "vault owner requires approval of withdraw"
  {:added "0.1"}
  [:string :memory vault-id
   :address user-address
   :string :memory withdraw-id
   :uint amount]
  (-/ut:assert-owner vault-id s/msg-sender)
  (var (-/WithdrawRequest :memory request)
       (. -/g:VaultWithdrawals [vault-id] [user-address] [withdraw-id]))
  (s/require (== (. request status)
                 (. -/VaultRequestStatus Confirmed))
             "Withdraw not confirmed")
  (s/require (== (. request amount) amount)
             "Withdraw amount incorrect")
  (-/ut:withdraw-transfer vault-id
                          user-address
                          withdraw-id
                          request
                          false))

(defn.sol ^{:- [:external]}
  owner:withdraw-reject
  "vault owner can reject withdraw"
  {:added "0.1"}
  [:string :memory vault-id
   :address user-address
   :string :memory withdraw-id]
  (-/ut:assert-owner vault-id s/msg-sender)
  (var (-/WithdrawRequest :memory request)
       (. -/g:VaultWithdrawals [vault-id] [user-address] [withdraw-id]))
  (s/require (== (. request status)
                 (. -/VaultRequestStatus Confirmed))
             "Withdraw not confirmed")
  (:= (. -/g:VaultWithdrawals [vault-id] [user-address] [withdraw-id] status)
      (. -/VaultRequestStatus Rejected))
  (:-= (. -/g:VaultLookup [vault-id] total-requested) (. request amount))
  (emit (-/VaultEvent "withdraw_reject"
                     withdraw-id
                     user-address
                     (. request amount))))

(defn.sol ^{:- [:external]}
  user:withdraw-self
  "can withdraw only with site approval"
  {:added "0.1"}
  [:string :memory vault-id
   :string :memory withdraw-id]
  (s/require (or (not= (. -/g:VaultLookup [vault-id] status)
                       (. -/VaultStatus Active)))
             "No self service.")
  (var (-/WithdrawRequest :memory request)
       (. -/g:VaultWithdrawals [vault-id] [s/msg-sender] [withdraw-id]))
  (s/require (== (. request status)
                 (. -/VaultRequestStatus Confirmed))
             "Withdraw not confirmed")
  (-/ut:withdraw-transfer vault-id
                          s/msg-sender
                          withdraw-id
                          request
                          true))

(defn.sol ^{:- [:external]}
  user:account-deposit
  "adds payment to the vault and user account"
  {:added "0.1"}
  [:string :memory vault-id
   :uint amount]
  (var (-/VaultAccount :memory account)
       (-/ut:get-account vault-id s/msg-sender))
  (s/require (== (. account status)
                 (. -/VaultAccountStatus Active))
             "Account not active.")
  (:+=  (. -/g:VaultLookup [vault-id] total-pool) amount)
  (var (-/IERC20 erc20) (-/IERC20 (. -/g:VaultLookup [vault-id] vault-erc20)))
  (. erc20 (transferFrom s/msg-sender
                         (address this)
                         amount))
  (emit (-/VaultEvent "account_deposit"
                     vault-id
                     s/msg-sender
                     amount)))

(def +default-contract+
  {:ns   (h/ns-sym)
   :name "StatstradeVaultErc20Factory"
   :args [30]})

(comment
  (s/with:open-methods
   (s/rt:bytecode-size +default-contract+))
  (s/rt:bytecode-size  +default-contract+))
