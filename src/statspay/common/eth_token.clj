^{:no-test true}
(ns statspay.common.eth-token
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

(def +abi+
  [{"name" "allowance",
    "stateMutability" "view",
    "outputs"
    [{"internalType" "uint256", "name" "", "type" "uint256"}],
    "type" "function",
    "inputs"
    [{"internalType" "address", "name" "", "type" "address"}
     {"internalType" "address", "name" "", "type" "address"}]}

   {"name" "approve",
    "stateMutability" "nonpayable",
    "outputs" [{"internalType" "bool", "name" "", "type" "bool"}],
    "type" "function",
    "inputs"
    [{"internalType" "address", "name" "usr", "type" "address"}
     {"internalType" "uint256", "name" "amount", "type" "uint256"}]}

   {"name" "balanceOf",
    "stateMutability" "view",
    "outputs"
    [{"internalType" "uint256", "name" "", "type" "uint256"}],
    "type" "function",
    "inputs" [{"internalType" "address", "name" "", "type" "address"}]}
   
   {"name" "decimals",
    "stateMutability" "view",
    "outputs" [{"internalType" "uint8", "name" "", "type" "uint8"}],
    "type" "function",
    "inputs" []}

   {"name" "name",
    "stateMutability" "view",
    "outputs" [{"internalType" "string", "name" "", "type" "string"}],
    "type" "function",
    "inputs" []}

   {"name" "nonces",
    "stateMutability" "view",
    "outputs"
    [{"internalType" "uint256", "name" "", "type" "uint256"}],
    "type" "function",
    "inputs" [{"internalType" "address", "name" "", "type" "address"}]}
   
   {"name" "symbol",
    "stateMutability" "view",
    "outputs" [{"internalType" "string", "name" "", "type" "string"}],
    "type" "function",
    "inputs" []}

   {"name" "totalSupply",
    "stateMutability" "view",
    "outputs"
    [{"internalType" "uint256", "name" "", "type" "uint256"}],
    "type" "function",
    "inputs" []}

   {"name" "transfer",
    "stateMutability" "nonpayable",
    "outputs" [{"internalType" "bool", "name" "", "type" "bool"}],
    "type" "function",
    "inputs"
    [{"internalType" "address", "name" "dst", "type" "address"}
     {"internalType" "uint256", "name" "amount", "type" "uint256"}]}

   {"name" "transferFrom",
    "stateMutability" "nonpayable",
    "outputs" [{"internalType" "bool", "name" "", "type" "bool"}],
    "type" "function",
    "inputs"
    [{"internalType" "address", "name" "src", "type" "address"}
     {"internalType" "address", "name" "dst", "type" "address"}
     {"internalType" "uint256", "name" "amount", "type" "uint256"}]}

   {"name" "version",
    "stateMutability" "view",
    "outputs" [{"internalType" "string", "name" "", "type" "string"}],
    "type" "function",
    "inputs" []}])

(defvar.js default-contract-address
  "default contract address"
  {:added "0.1"}
  []
  (return nil))

(def.js ABI
  (@! +abi+))

(h/template-entries [base-gen/tmpl-function {:abi -/ABI
                                             :top [owner-address spend-address]
                                             :contract-address -/default-contract-address
                                             :provider eth-common/default-provider
                                             :signer  eth-common/default-signer}]
  (filterv #(= "function" (get % "type"))
           +abi+))

(def.js MODULE (!:module))

(comment
  (g__SiteAuthority)
  (payment_native))
