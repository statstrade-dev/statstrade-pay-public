(ns statspay.common.eth-vault-native
  (:require [std.lang :as l]
            [std.lib :as h]
            [rt.postgres :as pg]
            [statspay.common.base-compile :as base-compile]
            [statspay.common.base-gen :as base-gen]))

(l/script :js
  {:require [[xt.lang.base-lib :as k]
             [xt.lang.base-runtime :as rt :with [defvar.js]]
             [statspay.common.eth-common :as eth-common]
             [js.lib.eth-lib :as eth-lib :include [:fn]]]
   :export  [MODULE]})

(defvar.js default-contract-address
  []
  (return nil))

(def.js ABI
  (@! (base-compile/get-vault-native-abi)))

(h/template-entries [base-gen/tmpl-function {:abi -/ABI
                                             :top [vault-id user-address withdraw-id]
                                             :contract-address -/default-contract-address
                                             :provider eth-common/default-provider
                                             :signer  eth-common/default-signer}]
  (filter #(= "function" (get % "type"))
          (base-compile/get-vault-native-abi)))

(def.js EVENT_TYPES
  {:vault-create true
   :account-open true
   :account-open-confirm true
   :account-close true
   :account-lock true
   :account-unlock true
   :arbitration-vote true
   :arbitration-unvote true
   :withdraw-request true
   :withdraw-confirm true
   :withdraw-reject-site true
   :withdraw-transfer true
   :withdraw-reject true
   :account-deposit  true})

(defn.js getLogs
  "gets logs given contract"
  {:added "0.1"}
  [eventFrom eventTo provider contract-address]
  (return (eth-common/logsFn "VaultEvent"
                             eventFrom
                             eventTo
                             -/ABI
                             -/default-contract-address
                             provider contract-address)))

(defn.js subscribe
  [f signer contract-address]
  (return (eth-common/subscribeFn "on"
                                  "VaultEvent"
                                  -/ABI
                                  -/default-contract-address
                                  (eth-common/eventFn f "vault")
                                  signer contract-address)))

(defn.js subscribeOnce
  [f signer contract-address]
  (return (eth-common/subscribeFn "once"
                                  "VaultEvent"
                                  -/ABI
                                  -/default-contract-address
                                  (eth-common/eventFn f "vault")
                                  signer contract-address)))


(def.js MODULE (!:module))
