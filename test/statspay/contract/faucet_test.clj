(ns statspay.contract.faucet-test
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

(defonce +dai-token-interface+
  (std.json/read
   (h/sys:resource-content
    "pay-assets/contract/erc20/Dai.json")))

(defmacro token-fn
  [name args]
  (h/$
   (j/<!
    (. (eth-lib/contract-run
        (eth-lib/get-signer "http://127.0.0.1:8545"
                            (@! (first env/+default-private-keys+)))
        (@! (get +deployed-token+
                 "contractAddress"))
        (@! (get +dai-token-interface+ "abi"))
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
     (@! (first env/+default-private-keys+))
     (@! (get +dai-token-interface+ "abi"))
     (@! (get-in +dai-token-interface+ ["data" "bytecode" "object"]))
     [1337]
     {:gasLimit 10000000}))))

(fact:global
 {:setup [(do (l/rt:restart)
              (env/stop-ganache-server)
              (env/start-ganache-server))
          (do (def +deployed-token+
                (token-deploy))
              (def +deployed-faucet-contract+
                (s/rt:contract
                 f/+default-contract+))
              (def +deployed-faucet+
                (s/with:caller-private-key [(first env/+default-private-keys+)]
                  (s/rt:deploy
                   f/+default-contract+))))]
  :teardown [(l/rt:stop)]})

^{:refer statspay.contract.faucet/CANARY :adopt true :added "0.1"
  :setup [(def +deployed-token+
            (token-deploy))
          (def +deployed-faucet+
            (s/with:caller-private-key [(first env/+default-private-keys+)]
              (s/rt:deploy
               f/+default-contract+)))
          (s/with:caller-private-key [(first env/+default-private-keys+)]
            (f/site:create-faucet "DAI"
                                  "DAI"
                                  (get +deployed-token+ "contractAddress")
                                  0
                                  0
                                  0))]}
(fact "creates the mint"
  ^:hiddn
  
  (token-fn "balanceOf"
            [(get +deployed-faucet+ "contractAddress")])
  => "0"

  (do (token-fn "mint"
                [(get +deployed-faucet+ "contractAddress")
                 "100000000000"])
      (token-fn "balanceOf"
                [(get +deployed-faucet+ "contractAddress")]))
  => "100000000000"
  
  (s/with:contract-address [(get +deployed-faucet+
                                 "contractAddress")]
    (s/with:gas-limit [100000]
      (s/with:caller-private-key [(second env/+default-private-keys+)]
        (f/user:request-topup "DAI" "1000000"))))
  => map?

  (s/with:contract-address [(get +deployed-faucet+
                                 "contractAddress")]
    (s/with:caller-private-key [(second env/+default-private-keys+)]
      (f/user:request-balance-caller "DAI")))
  => 1000000

  (s/with:contract-address [(get +deployed-faucet+
                                 "contractAddress")]
    (s/with:caller-private-key [(second env/+default-private-keys+)]
      (f/user:request-balance-faucet "DAI")))
  => 99999000000)

^{:refer statspay.contract.faucet/ut:assert-admin :added "0.1"}
(fact "assets that caller is faucet admin")

^{:refer statspay.contract.faucet/site:change-authority :added "0.1"
  :setup [(def +deployed-faucet+
            (s/with:caller-private-key [(last env/+default-private-keys+)]
              (s/rt:deploy
               f/+default-contract+)))]}
(fact "changes the site authority"
  ^:hidden
  
  (s/with:caller-private-key [(last env/+default-private-keys+)]
    (s/with:measure
     (f/site:change-authority (nth env/+default-addresses+ 3))))
  => (contains-in
      [(approx 0.05 0.02)
       map?]))

^{:refer statspay.contract.faucet/site:add-support :added "0.1"}
(fact "adds site support")

^{:refer statspay.contract.faucet/site:remove-support :added "0.1"}
(fact "removes site support")

^{:refer statspay.contract.faucet/site:create-faucet :added "0.1"
  :setup [(def +deployed-token+
            (token-deploy))
          (def +deployed-faucet+
            (s/with:caller-private-key [(first env/+default-private-keys+)]
              (s/rt:deploy
               f/+default-contract+)))
          ]}
(fact "creates a faucet"
  ^:hidden
  
  (s/with:caller-private-key [(first env/+default-private-keys+)]
    (s/with:measure
     (f/site:create-faucet "DAI"
                           "DAI"
                           (get +deployed-token+ "contractAddress")
                           0
                           0
                           0)))
  => (contains-in
      [(approx 0.16 0.05)
       map?]))

^{:refer statspay.contract.faucet/site:remove-faucet :added "0.1"}
(fact "TODO")

^{:refer statspay.contract.faucet/site:limit-amount :added "0.1"
  :setup [(def +deployed-token+
            (token-deploy))
          (def +deployed-faucet+
            (s/with:caller-private-key [(first env/+default-private-keys+)]
              (s/rt:deploy
               f/+default-contract+)))
          (s/with:caller-private-key [(first env/+default-private-keys+)]
            (f/site:create-faucet "DAI"
                                  "DAI"
                                  (get +deployed-token+ "contractAddress")
                                  0
                                  0
                                  0))]}
(fact "limits the amount"
  ^:hidden

  (s/with:caller-private-key [(first env/+default-private-keys+)]
    (s/with:measure
     (f/site:limit-amount "DAI" 100000)))
  => (contains-in
      [(approx 0.04229826143753
               0.02) map?]))

^{:refer statspay.contract.faucet/site:limit-frequency :added "0.1"
  :setup [(def +deployed-token+
            (token-deploy))
          (def +deployed-faucet+
            (s/with:caller-private-key [(first env/+default-private-keys+)]
              (s/rt:deploy
               f/+default-contract+)))
          (s/with:caller-private-key [(first env/+default-private-keys+)]
            (f/site:create-faucet "DAI"
                                  "DAI"
                                  (get +deployed-faucet+ "contractAddress")
                                  0
                                  0
                                  0))]}
(fact "limits the frequency"
  ^:hidden

  (s/with:caller-private-key [(first env/+default-private-keys+)]
    (s/with:measure
     (f/site:limit-frequency "DAI" 100000)))
  => (contains-in
      [(approx 0.04229826143753
               0.02) map?]))

^{:refer statspay.contract.faucet/site:limit-balance :added "0.1"
  :setup [(def +deployed-token+
            (token-deploy))
          (def +deployed-faucet+
            (s/with:caller-private-key [(first env/+default-private-keys+)]
              (s/rt:deploy
               f/+default-contract+)))
          (s/with:caller-private-key [(first env/+default-private-keys+)]
            (f/site:create-faucet "DAI"
                                  "DAI"
                                  (get +deployed-token+ "contractAddress")
                                  0
                                  0
                                  0))]}
(fact "limits the balance"
  ^:hidden

  (s/with:caller-private-key [(first env/+default-private-keys+)]
    (s/with:measure
     (f/site:limit-balance "DAI" 100000)))
  => (contains-in
      [(approx 0.04229826143753
               0.02) map?]))

^{:refer statspay.contract.faucet/user:request-balance-faucet :added "0.1"}
(fact "requests the token balance of faucet")

^{:refer statspay.contract.faucet/user:request-balance-caller :added "0.1"}
(fact "requests the token balance of caller")

^{:refer statspay.contract.faucet/user:request-topup :added "0.1"}
(fact "requests a token top up")

^{:refer statspay.contract.faucet/user:request-test-transfer :added "0.1"}
(fact "TODO")
