(ns statspay.common.eth-council
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
  (@! (base-compile/get-council-abi)))

(h/template-entries [base-gen/tmpl-function {:abi -/ABI
                                             :top [text addr]
                                             :contract-address -/default-contract-address
                                             :provider eth-common/default-provider
                                             :signer  eth-common/default-signer}]
  (filterv #(= "function" (get % "type"))
           (base-compile/get-council-abi)))

(def.js EVENT_TYPES
  {:member_add true
   :member_remove true
   :member_take_profit true})

(defn.js getLogs
  "gets logs given contract"
  {:added "0.1"}
  [eventFrom eventTo provider contract-address]
  (return (eth-common/logsFn "CouncilEvent"
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
                                  "CouncilEvent"
                                  -/ABI
                                  -/default-contract-address
                                  (eth-common/eventFn f "council")
                                  provider contract-address)))

(defn.js subscribeOnce
  "subscribes once to the council"
  {:added "0.1"}
  [f provider contract-address]
  (return (eth-common/subscribeFn "once"
                                  "CouncilEvent"
                                  -/ABI
                                  -/default-contract-address
                                  (eth-common/eventFn f "council")
                                  provider contract-address)))

(def.js MODULE (!:module))

(comment
  (g__SiteAuthority)
  (payment_native))
