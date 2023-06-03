(ns statspay.common.base-compile
  (:require [std.lang :as l]
            [std.lib :as h]
            [std.json :as json]
            [std.fs :as fs]
            [statspay.contract.council :as council]
            [statspay.contract.faucet :as faucet]
            [statspay.contract.gateway :as gateway]
            [statspay.contract.vault-erc20 :as vault-erc20]
            [statspay.contract.vault-native :as vault-native]
            [std.make :as make :refer [def.make]]))

(def.make PROJECT
  {:build    ".build/pay-contracts"
   :default  [{:type   :contract.sol
               :lang   :solidity
               :target "solidity"
               :main   [statspay.contract.council/+default-contract+
                        statspay.contract.faucet/+default-contract+
                        statspay.contract.gateway/+default-contract+
                        statspay.contract.vault-erc20/+default-contract+
                        statspay.contract.vault-native/+default-contract+]}
              {:type   :contract.abi
               :lang   :solidity
               :target "interface"
               :main   [statspay.contract.council/+default-contract+
                        statspay.contract.faucet/+default-contract+
                        statspay.contract.gateway/+default-contract+
                        statspay.contract.vault-erc20/+default-contract+
                        statspay.contract.vault-native/+default-contract+]}]})

(def +target+
  "pay-assets/contract/core")

(defn compile-contracts
  []
  (std.fs/delete (str "resources/" +target+))
  (make/build PROJECT)
  (std.fs/move ".build/pay-contracts" (str "resources/" +target+)))

(defn compiled-abi-fn
  [path & [target]]
  (fn []
    (get (std.json/read
          (h/sys:resource-content
           (str (or target +target+) path)))
         "abi")))

(defn compiled-bytecode-fn
  [path & [target access]]
  (fn []
    (get-in (std.json/read
             (h/sys:resource-content
              (str (or target +target+) path)))
            (or access
                ["evm" "bytecode" "object"]))))

;;
;; RETRIEVE
;;


(def ^{:arglists '([])}
  get-play-token-abi
  (compiled-abi-fn "/Play.json"
                   "pay-assets/contract/erc20/"))

(def ^{:arglists '([])}
  get-play-token-bytecode
  (compiled-bytecode-fn "/Play.json"
                        "pay-assets/contract/erc20/"
                        ["data" "bytecode" "object"]))

;;
;; SINGLE
;;

(def ^{:arglists '([])}
  get-council-abi
  (compiled-abi-fn "/interface/StatstradeCouncil.json"))

(def ^{:arglists '([])}
  get-council-bytecode
  (compiled-bytecode-fn "/interface/StatstradeCouncil.json"))

(def ^{:arglists '([])}
  get-gateway-abi
  (compiled-abi-fn "/interface/StatstradeGateway.json"))

(def ^{:arglists '([])}
  get-gateway-bytecode
  (compiled-bytecode-fn "/interface/StatstradeGateway.json"))

;;
;; MULIT
;;

(def ^{:arglists '([])}
  get-faucet-abi
  (compiled-abi-fn "/interface/StatstradeFaucetFactory.json"))

(def ^{:arglists '([])}
  get-faucet-bytecode
  (compiled-bytecode-fn "/interface/StatstradeFaucetFactory.json"))

(def ^{:arglists '([])}
  get-vault-erc20-abi
  (compiled-abi-fn "/interface/StatstradeVaultErc20Factory.json"))

(def ^{:arglists '([])}
  get-vault-erc20-bytecode
  (compiled-bytecode-fn "/interface/StatstradeVaultErc20Factory.json"))

(def ^{:arglists '([])}
  get-vault-native-abi
  (compiled-abi-fn "/interface/StatstradeVaultNativeFactory.json"))

(def ^{:arglists '([])}
  get-vault-native-bytecode
  (compiled-bytecode-fn "/interface/StatstradeVaultNativeFactory.json"))

(comment
  (compile-contracts)
  
  (get-gateway-abi)
  (get-vault-erc20-abi)
  
  
  (def ^{:arglists '([])}
    get-tournament-erc20-abi
    (compiled-abi-fn "/interface/StatstradeTournamentFactory.json"))

  (def ^{:arglists '([])}
    get-tournament-erc20-bytecode
    (compiled-bytecode-fn "/interface/StatstradeTournamentFactory.json")))


