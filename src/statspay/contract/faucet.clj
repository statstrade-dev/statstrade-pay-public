(ns statspay.contract.faucet
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

(defenum.sol FaucetStatus
  [:Undefined :Active])

(def.sol ^{:- [:address :public]}
  g:SiteAuthority)

(defmapping.sol ^{:- [:public]}
  g:SiteSupport
  [:address :bool])

(defmapping.sol ^{:- [:public]}
  g:SiteRequests
  [:string (:mapping [:address :uint])])

(defstruct.sol Faucet
  [:% -/FaucetStatus status]
  [:string      name]
  [:address     token-address]
  [:uint        limit-amount]
  [:uint        limit-frequency]
  [:uint        limit-balance])

(defmapping.sol ^{:- [:public]}
  g:FaucetLookup
  [:string  -/Faucet])


;;
;; CONTRUCTOR
;;

(defconstructor.sol
  __init__
  []
  (:= g:SiteAuthority s/msg-sender))

(defevent.sol FaucetEvent
  [:string     event-type]
  [:string     event-id]
  [:address    sender]
  [:uint       value])

(defn.sol ^{:- [:external]}
  site:change-authority
  "changes the site authority"
  {:added "0.1"}
  [:address new-authority]
  (require (== s/msg-sender -/g:SiteAuthority)
           "Site authority only.")
  (:= -/g:SiteAuthority new-authority))

(defn.sol ^{:- [:internal :view]}
  ut:assert-admin
  "assets that caller is faucet admin"
  {:added "0.1"}
  [:address user-address]
  (s/require (or (== user-address -/g:SiteAuthority)
                 (. -/g:SiteSupport [user-address]))
             "Site admin only."))

(defn.sol ^{:- [:external]}
  site:change-authority
  "changes the site authority"
  {:added "0.1"}
  [:address new-authority]
  (require (== s/msg-sender -/g:SiteAuthority)
           "Site authority only.")
  (:= -/g:SiteAuthority new-authority))

(defn.sol ^{:- [:external]}
  site:add-support
  "adds site support"
  {:added "0.1"}
  [:address user]
  (s/require (== s/msg-sender -/g:SiteAuthority)
             "Site authority only.")
  (:= (. -/g:SiteSupport [user]) true))

(defn.sol ^{:- [:external]}
  site:remove-support
  "removes site support"
  {:added "0.1"}
  [:address user]
  (-/ut:assert-admin s/msg-sender)
  (delete (. -/g:SiteSupport [user])))


;;
;; METHODS
;;

(defn.sol ^{:- [:external]}
  site:create-faucet
  "creates a faucet"
  {:added "0.1"}
  [:string :memory faucet-id
   :string :memory name
   :address     token-address
   :uint        limit-amount
   :uint        limit-frequency
   :uint        limit-balance]
  (-/ut:assert-admin s/msg-sender)
  (s/require (== (. -/FaucetStatus Undefined)
                 (. -/g:FaucetLookup [faucet-id] status))
             "Faucet already exists.")
  (var (-/Faucet :memory faucet)
       (-/Faucet
        {:status (. -/FaucetStatus Active)
         :name  name
         :token-address token-address
         :limit-amount limit-amount
         :limit-frequency limit-frequency
         :limit-balance limit-balance}))
  (:= (. -/g:FaucetLookup [faucet-id]) faucet)
  (emit (-/FaucetEvent "faucet_create"
                       faucet-id
                       token-address
                       0)))

(defn.sol ^{:- [:external]}
  site:remove-faucet
  [:string :memory faucet-id :uint limit]
  (ut:assert-admin s/msg-sender)
  (delete (. -/g:FaucetLookup [faucet-id])))

(defn.sol ^{:- [:external]}
  site:limit-amount
  "limits the amount"
  {:added "0.1"}
  [:string :memory faucet-id :uint limit]
  (ut:assert-admin s/msg-sender)
  (s/require (or (== (. -/g:FaucetLookup [faucet-id] status)
                     (. -/FaucetStatus Active)))
             "Faucet not found")
  (== (. -/g:FaucetLookup [faucet-id] limit-amount)
      limit))

(defn.sol ^{:- [:external]}
  site:limit-frequency
  "limits the frequency"
  {:added "0.1"}
  [:string :memory faucet-id :uint limit]
  (ut:assert-admin s/msg-sender)
  (s/require (or (== (. -/g:FaucetLookup [faucet-id] status)
                     (. -/FaucetStatus Active)))
             "Faucet not found")
  (== (. -/g:FaucetLookup [faucet-id] limit-frequency)
      limit))

(defn.sol ^{:- [:external]}
  site:limit-balance
  "limits the balance"
  {:added "0.1"}
  [:string :memory faucet-id :uint limit]
  (ut:assert-admin s/msg-sender)
  (s/require (or (== (. -/g:FaucetLookup [faucet-id] status)
                     (. -/FaucetStatus Active)))
             "Faucet not found")
  (== (. -/g:FaucetLookup [faucet-id] limit-balance)
      limit))

(defn.sol ^{:- [:external :view]
            :static/returns :uint}
  user:request-balance-faucet
  "requests the token balance of faucet"
  {:added "0.1"}
  [:string :memory faucet-id]
  (s/require (or (== (. -/g:FaucetLookup [faucet-id] status)
                     (. -/FaucetStatus Active)))
             "Faucet not found")
  (var (:address token-address)
       (. -/g:FaucetLookup [faucet-id] token-address))
  (var (-/IERC20 erc20) (-/IERC20 token-address))
  (return (. erc20 (balanceOf (address this)))))

(defn.sol ^{:- [:external :view]
            :static/returns :uint}
  user:request-balance-caller
  "requests the token balance of caller"
  {:added "0.1"}
  [:string :memory faucet-id]
  (s/require (or (== (. -/g:FaucetLookup [faucet-id] status)
                     (. -/FaucetStatus Active)))
             "Faucet not found")
  (var (:address token-address)
       (. -/g:FaucetLookup [faucet-id] token-address))
  (var (-/IERC20 erc20) (-/IERC20 token-address))
  (return (. erc20 (balanceOf (address s/msg-sender)))))

(defn.sol ^{:- [:external]}
  user:request-topup
  "requests a token top up"
  {:added "0.1"}
  [:string :memory faucet-id :uint amount]
  (var (-/Faucet :memory faucet) (. -/g:FaucetLookup [faucet-id]))
  (s/require (or (== (. faucet status)
                     (. -/FaucetStatus Active)))
             "Faucet not found")
  (var (:uint prev)  (. -/g:SiteRequests [faucet-id] [s/msg-sender]))
  (require (or (== 0 (. faucet limit-frequency))
               (< (+ prev (. faucet limit-frequency))
                  s/block-timestamp))
           "Request frequency over limit")
  (require (or (== 0 (. faucet limit-amount))
               (< amount
                  (. faucet limit-amount)))
           "Request amount over limit")
  
  (var (-/IERC20 erc20) (-/IERC20 (. faucet token-address)))
  (var (:uint balance) (. erc20 (balanceOf s/msg-sender)))
  (require (or (== 0 (. faucet limit-balance))
               (<= (+ balance amount)
                   (. faucet limit-balance)))
           "Request balance over limit")
  
  (. erc20 (transfer s/msg-sender amount))
  (:= (. -/g:SiteRequests [faucet-id] [s/msg-sender]) s/block-timestamp)
  (emit (-/FaucetEvent "faucet_topup"
                       faucet-id
                       s/msg-sender
                       amount)))

(defn.sol ^{:- [:external]}
  user:request-test-transfer
  "requests a token top up"
  {:added "0.1"}
  [:address token-address :address user-address]
  (var (-/IERC20 erc20) (-/IERC20 token-address))
  (. erc20 (transfer user-address 1)))

(defonce +default-contract-sym+
  (h/ns-sym))

(def +default-contract+
  {:ns   +default-contract-sym+  
   :name "StatstradeFaucetFactory"
   :args []})
