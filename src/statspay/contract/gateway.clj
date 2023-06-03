(ns statspay.contract.gateway
  (:require [std.lang :as l]
            [std.lib :as h]
			[rt.solidity :as s]))

(l/script :solidity
  {:require [[rt.solidity :as s]]})

(defevent.sol GatewayEvent
  [:string     event-type]
  [:string     event-id]
  [:address    sender]
  [:uint       value])

(defaddress.sol ^{:- [:public]}
  g:SiteAuthority)

(defmapping.sol ^{:- [:public]}
  g:SiteSupport
  [:address :bool])

(defmapping.sol ^{:- [:public]}
  g:SitePaymasters
  [:string :address])

;;
;; CONTRUCTOR
;;

(defconstructor.sol
  __init__
  []
  (:= -/g:SiteAuthority s/msg-sender))

(defn.sol ^{:- [:internal :view]}
  ut:assert-admin
  "asserts that address is admin"
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
  site:set-paymaster
  "adds supporting keys to the contract"
  {:added "4.0"}
  [:string :memory pay-id :address user]
  (:= (. -/g:SitePaymasters [pay-id])
      user))

(defn.sol ^{:- [:external]}
  site:del-paymaster
  "adds supporting keys to the contract"
  {:added "4.0"}
  [:string :memory pay-id]
  (-/ut:assert-admin s/msg-sender)
  (delete (. -/g:SitePaymasters [pay-id])))

;;
;; METHODS
;;

(defn.sol ^{:- [:external :payable]}
  payment-native
  "makes a payment"
  {:added "0.1"}
  [:string :memory pay-id]
  (s/require (not= s/msg-value 0)
             "Cannot be zero")
  (. (payable  (:? (== (. -/g:SitePaymasters [pay-id])
                       (address 0))
                   -/g:SiteAuthority
                   (. -/g:SitePaymasters [pay-id])))
     (transfer s/msg-value))
  (emit (-/GatewayEvent "payment_native"
                        pay-id
                        s/msg-sender
                        s/msg-value)))

(def +default-contract+
  {:ns   (h/ns-sym)
   :name "StatstradeGateway"
   :args []})

(comment
  (s/rt:bytecode-size +default-contract+))
