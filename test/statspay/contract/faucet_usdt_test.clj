(ns statspay.contract.faucet-usdt-test
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

(def +usdt-token-interface+
  (std.json/read
   (h/sys:resource-content
    "pay-assets/contract/erc20/USDT.json")))

(def ^:dynamic *deployed-token* nil)

(defn token-deploy
  []
  (eval
   (h/$
    (j/<!
     (eth-bench/contract-deploy
      "http://127.0.0.1:8545"
      (@! (nth env/+default-private-keys+ 0))
      (@! (get +usdt-token-interface+ "abi"))
      (@! (get-in +usdt-token-interface+ ["data" "bytecode" "object"]))
      []
      {})))))

(defmacro token-fn
  [name args]
  (h/$
   (j/<!
    (. (eth-lib/contract-run
        (eth-lib/get-signer "http://127.0.0.1:8545"
                            (@! (nth env/+default-private-keys+ 0)))
        (@! (get *deployed-token*
                 "contractAddress"))
        (@! (get +usdt-token-interface+ "abi"))
        (@! ~name)
        (@! ~args)
        {:gasLimit 10000000})
       (then (fn [x]
               (return
                (:? (== "BigNumber"
                        (k/type-native x))
                    (k/to-string x)
                    x))))))))

(comment
  #_
  (defn token-initialise
    []
    (eval
     (h/$
      (j/<!
       (eth-lib/contract-run
        (e/get-signer "http://127.0.0.1:8545"
                      (@! (nth env/+default-private-keys+ 0)))
        (@! +address+)
        (@! (get +usdt-token-interface+ "abi"))
        "initialize"
        ["USDT"
         "USDT"
         6
         (nth env/+default-private-keys+ 0)]
        {}))))))


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
                (s/with:caller-private-key [(nth env/+default-private-keys+ 0)]
                  (s/rt:contract
                   f/+default-contract+))))]
  :teardown [(l/rt:stop)]})

^{:refer statspay.contract.faucet/CANARY :adopt true :added "0.1"
  :setup [(def ^:dynamic *deployed-token*
            (token-deploy))
          (token-fn "initialize"
                    ["USDT"
                     "USDT"
                     6
                     (nth env/+default-addresses+ 0)])
          (token-fn "deposit"
                    [(nth env/+default-addresses+ 0)
                     "0x0000000000000000000000000000000000000000033b2e3c9fd0803ce8000000"
                     #_
                     (!.js
                      (ethers.utils.hexZeroPad
                       (. (eth-lib/to-bignum "1000000000000000000000000000")
                          (toHexString))
                       32))
                     
                     ])
          (def +deployed-faucet+
            (s/with:caller-private-key [(nth env/+default-private-keys+ 0)]
              (s/rt:deploy
               f/+default-contract+)))]}
(fact "creates the mint"
  ^:hiddn

  (token-fn "balanceOf"
            [(get +deployed-faucet+ "contractAddress")])
  => "0"

  (token-fn "balanceOf"
            [(first env/+default-addresses+)])
  => "1000000000000000000000000000"

  (token-fn "transfer"
            [(get +deployed-faucet+ "contractAddress")
             "100000000000"])
  
  (token-fn "balanceOf"
            [(get +deployed-faucet+ "contractAddress")])
  => "100000000000"
  
  (token-fn "transfer"
            [(get +deployed-faucet+ "contractAddress")
             "200000000000"])

  #_#_#_#_#_#_#_#_#_#_#_#_#_#_#_#_#_
  
  (s/with:contract-address [(get +deployed-faucet+
                                 "contractAddress")]
    (s/with:caller-private-key [(nth env/+default-private-keys+ 0)]
      (f/site:create-faucet "USDT"
                            "USDT"
                            (get +deployed-token+ "contractAddress")
                            0
                            0
                            0)))
  
  (s/with:contract-address [(get +deployed-faucet+
                                 "contractAddress")]
    (s/with:caller-private-key [(nth env/+default-private-keys+ 1)]
      (f/user:request-balance-caller "USDT")))
  => 0
  
  (s/with:contract-address [(get +deployed-faucet+
                                 "contractAddress")]
    (s/with:caller-private-key [(nth env/+default-private-keys+ 1)]
      (f/user:request-balance-faucet "USDT")))
  => integer?


  (comment
    (s/with:contract-address [(get +deployed-faucet+
                                   "contractAddress")]
      (s/with:caller-private-key [(nth env/+default-private-keys+ 1)]
        (f/user:transfer-tiny (get +deployed-token+ "contractAddress")
                              (nth env/+default-addresses+ 1)
                              ))))
  
  (s/with:contract-address [(get +deployed-faucet+
                                 "contractAddress")]
    (s/with:caller-private-key [(nth env/+default-private-keys+ 1)]
      (f/user:request-topup "USDT" "1000000")))
  => map?


  (s/with:contract-address [(get +deployed-faucet+
                                 "contractAddress")]
    (s/with:caller-private-key [(nth env/+default-private-keys+ 2)]
      (f/user:request-topup "USDT" "1000000")))
  => map?
  
  (def +out+
    (j/<!
     (. (eth-lib/contract-run
         (eth-lib/get-signer "http://127.0.0.1:8545"
                             (@! (nth env/+default-private-keys+ 1)))
         (@! (get +deployed-faucet+
                  "contractAddress"))
         (@! (:abi +deployed-faucet-contract+))
         "user__request_topup"
         ["USDT"
          "1"]
         {:gasLimit 1000000})
        (then (fn:> [res e]
                (. res (wait))))
        (catch (fn:> [e]
                 (. e message))))))
  +out+
  => string?)



(comment

  (token-fn "transfer"
            [(get +deployed-faucet+ "contractAddress")
             "100000000000"]))
