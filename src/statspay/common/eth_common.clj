(ns statspay.common.eth-common
  (:require [std.lang :as l]
            [std.lib :as h]
            [rt.postgres :as pg]
            [statspay.common.base-compile :as base-compile]
            [statspay.common.base-gen :as base-gen]))

(l/script :js
  {:require [[xt.lang.base-lib :as k]
             [xt.lang.base-runtime :as rt :with [defvar.js]]
             [js.lib.eth-lib :as eth-lib :include [:fn]]
             [js.core :as j]]
   :export  [MODULE]})

(defvar.js default-provider
  "gets the default provider"
  {:added "0.1"}
  []
  (return nil))

(defvar.js default-signer
  "gets the default signer"
  {:added "0.1"}
  []
  (return nil))

(defn.js logsFn
  "skeleton log function"
  {:added "0.1"}
  [eventKey eventFrom eventTo abi default-contract-fn provider contract-address]
  (var contract (eth-lib/new-contract (or contract-address
                                          (default-contract-fn))
                                      abi
                                      (or provider
                                          (-/default-provider))))
  (return (. contract (queryFilter eventKey eventFrom eventTo))))

(defn.js subscribeFn
  "skeleton subscribe function"
  {:added "0.1"}
  [eventFn eventKey abi default-contract-fn f provider contract-address]
  (var contract (eth-lib/new-contract (or contract-address
                                          (default-contract-fn))
                                      abi
                                      (or provider
                                          (-/default-provider))))
  ((. contract [eventFn]) eventKey f)
  (return (fn []
            (. contract (removeAllListeners)))))

(defn.js processFn
  "processes an event"
  {:added "0.1"}
  [event-prefix event]
  (var #{args} event)
  (var [event-type event-id event-address event-amount] args)
  (var #{blockNumber blockHash
         transactionHash} event)
  (return
   {:txid      transactionHash
    :type      (+ event-prefix "." event-type)
    :srcid     event-id
    :address   event-address
    :amount    (eth-lib/to-number-string event-amount)
    :blockid   blockHash
    :blocknum  blockNumber
    :raw       event}))

(defn.js eventFn
  "skeleton event function"
  {:added "0.1"}
  [f event-prefix]
  (return
   (fn [event-type event-id event-address event-amount event]
     (return (f (-/processFn event-prefix event))))))

(def.js MODULE (!:module))


(comment
  ())
