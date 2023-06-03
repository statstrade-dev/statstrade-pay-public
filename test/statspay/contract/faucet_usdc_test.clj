(ns statspay.contract.faucet-usdc-test
  (:use code.test)
  (:require [std.lang :as l]
            [std.lib :as h]
            [std.string :as str]
            [rt.solidity :as s]
            [statspay.common.env-ganache :as env]))

(l/script- :solidity
  {:runtime :web3
   :require [[rt.solidity :as s]
             [statspay.contract.faucet :as f]]})

(l/script- :js
  {:runtime :basic
   :require [[xt.lang.base-lib :as k]
             [xt.lang.base-repl :as repl]
             [js.lib.eth-lib :as eth-lib :include [:fn]]
             [js.lib.eth-bench :as eth-bench]
             [js.core :as j]]})

(defonce +usdc-token-interface+
  (std.json/read
   (h/sys:resource-content
    "pay-assets/contract/erc20/USDC.json")))

(defmacro token-fn
  [name args]
  (h/$
   (j/<!
    (. (eth-lib/contract-run
        (eth-lib/get-signer "http://127.0.0.1:8545"
                            (@! (nth env/+default-private-keys+ 0)))
        (@! (get +deployed-token+
                 "contractAddress"))
        (@! (get +usdc-token-interface+ "abi"))
        (@! ~name)
        (@! ~args)
        {})
       (then (fn [x]
               (return
                (:? (== "BigNumber"
                        (k/type-native x))
                    (k/to-string x)
                    x))))))))

(defmacro token-deploy
  []
  (h/$
   (j/<!
    (eth-bench/contract-deploy
     "http://127.0.0.1:8545"
     (@! (nth env/+default-private-keys+ 0))
     (@! (get +usdc-token-interface+ "abi"))
     (@! (get-in +usdc-token-interface+ ["data" "bytecode" "object"]))
     []
     {:gasLimit 100000000}))))

(fact:global
 {:setup [(do (l/rt:restart)
              (env/stop-ganache-server)
              (Thread/sleep 500)
              (env/start-ganache-server
               ["ganache"
                "--gasLimit" "10000000000"
                "--chain.allowUnlimitedContractSize"
                "--wallet.seed" "test"
                "--host" "0.0.0.0"])
              (Thread/sleep 500))
          (do (def +deployed-faucet-contract+
                (s/rt:contract
                 f/+default-contract+))
              
              (def +deployed-faucet+
                (s/with:caller-private-key [(nth env/+default-private-keys+ 0)]
                  (s/rt:deploy
                   f/+default-contract+))))]
  :teardown [(l/rt:stop)]})

^{:refer statspay.contract.faucet/CANARY :adopt true :added "0.1"
  :setup [(def +deployed-token+
            (token-deploy))
          (def +deployed-faucet+
            (s/with:caller-private-key [(nth env/+default-private-keys+ 0)]
              (s/rt:deploy
               f/+default-contract+)))]}
(fact "creates the mint"
  ^:hiddn

  (token-fn "balanceOf"
            [(get +deployed-faucet+ "contractAddress")])
  => "0"
  
  (do (token-fn "initialize"
                ["USDC"
                 "USDC"
                 "USDC"
                 6
                 (nth env/+default-addresses+ 0) 
                 (nth env/+default-addresses+ 0) 
                 (nth env/+default-addresses+ 0) 
                 (nth env/+default-addresses+ 0)])
      (token-fn "configureMinter"
                [(nth env/+default-addresses+ 0)
                 "100000000000000000"])
      (token-fn "mint"
                [(get +deployed-faucet+ "contractAddress")
                 "100000000000"]))
  
  (token-fn "balanceOf"
            [(get +deployed-faucet+ "contractAddress")])
  => "100000000000"
  
  (s/with:caller-private-key [(nth env/+default-private-keys+ 0)]
    (f/site:create-faucet "USDC"
                          "USDC"
                          (get +deployed-token+ "contractAddress")
                          0
                          0
                          0))
  
  
  (s/with:contract-address [(get +deployed-faucet+
                                 "contractAddress")]
    (s/with:caller-private-key [(nth env/+default-private-keys+ 1)]
      (f/user:request-topup "USDC" "1000000")))
  => map?

  (s/with:contract-address [(get +deployed-faucet+
                                 "contractAddress")]
    (s/with:caller-private-key [(nth env/+default-private-keys+ 1)]
      (f/user:request-balance-caller "USDC")))
  => 1000000

  (s/with:contract-address [(get +deployed-faucet+
                                 "contractAddress")]
    (s/with:caller-private-key [(nth env/+default-private-keys+ 1)]
      (f/user:request-balance-faucet "USDC")))
  => 99999000000)
