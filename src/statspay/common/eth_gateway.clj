(ns statspay.common.eth-gateway
  (:require [std.lang :as l]
            [std.lib :as h]
            [rt.postgres :as pg]
            [statspay.common.base-compile :as base-compile]
            [statspay.common.base-gen :as base-gen]))

(l/script :js
  {:require [[xt.lang.base-lib :as k]
             [xt.lang.base-runtime :as rt :with [defvar.js]]
             [statspay.common.eth-common :as eth-common]
             [js.lib.eth-lib :as eth-lib :include [:fn]]
             [js.core :as j]]
   :export  [MODULE]})

(defvar.js default-contract-address
  "default contract address"
  {:added "0.1"}
  []
  (return nil))

(def.js ABI
  (@! (base-compile/get-gateway-abi)))

(h/template-entries [base-gen/tmpl-function
                     {:abi -/ABI
                      :top [pay-id]
                      :contract-address -/default-contract-address
                      :provider eth-common/default-provider
                      :signer  eth-common/default-signer}]
  (filter #(= "function" (get % "type"))
          (base-compile/get-gateway-abi)))

(def.js EVENT_TYPES
  {:link-account true
   :payment-native true})

(defn.js getLogs
  "gets logs given contract"
  {:added "0.1"}
  [eventFrom eventTo provider contract-address]
  (return (eth-common/logsFn "GatewayEvent"
                             eventFrom
                             eventTo
                             -/ABI
                             -/default-contract-address
                             provider contract-address)))

(defn.js subscribe
  "subscribes to the event"
  {:added "0.1"}
  [f provider contract-address]
  (return (eth-common/subscribeFn "on"
                                  "GatewayEvent"
                                  -/ABI
                                  -/default-contract-address
                                  (eth-common/eventFn f "gateway")
                                  provider contract-address)))

(defn.js subscribeOnce
  "subscribes once to the gateway"
  {:added "0.1"}
  [f provider contract-address]
  (return (eth-common/subscribeFn "once"
                                  "GatewayEvent"
                                  -/ABI
                                  -/default-contract-address
                                  (eth-common/eventFn f "gateway")
                                  provider contract-address)))

(def.js MODULE (!:module))

(comment
  (g__SiteAuthority)
  (payment_native))
