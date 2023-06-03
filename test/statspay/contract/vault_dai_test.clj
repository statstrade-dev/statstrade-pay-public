(ns statspay.contract.vault-dai-test
  (:use code.test)
  (:require [std.lang :as l]
            [std.lib :as h]
            [std.string :as str]
            [rt.solidity]
            [statspay.common.env-ganache :as env]))

(l/script- :solidity
  {:runtime :web3
   :require [[rt.solidity :as s]
             [statspay.contract.vault-erc20 :as t]
             [statspay.common.eth-test-erc20 :as source]]})

(l/script :js
  {:runtime :basic
   :require [[xt.lang.base-lib :as k]
             [xt.lang.base-repl :as repl]
             [js.lib.eth-lib :as eth-lib :include [:fn]]
             [js.lib.eth-bench :as eth-bench]
             [js.core :as j]]})

(def +dai-token-interface+
  (std.json/read
   (h/sys:resource-content
    "pay-assets/contract/erc20/Dai.json")))

(defmacro token-fn
  [name args]
  (h/$
   (j/<!
    (. (eth-lib/contract-run
        (eth-lib/get-signer "http://127.0.0.1:8545"
                            (@! (nth env/+default-private-keys+ 9)))
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
     (@! (nth env/+default-private-keys+ 9))
     (@! (get +dai-token-interface+ "abi"))
     (@! (get-in +dai-token-interface+ ["data" "bytecode" "object"]))
     [1337]
     {:gasLimit 10000000}))))

(defonce ^:dynamic *erc20-address* nil)

(defn prep-dai
  []
  (do (def +deployed-token+
        (token-deploy))
      (def ^:dynamic *erc20-address*
        (get +deployed-token+ "contractAddress"))
      (token-fn "mint" [(nth env/+default-addresses+ 9)
                        "10000000000000000"])
      (def +address+ nil)
      (doseq [address (take 9 env/+default-addresses+)]
        (def +address+ address)
        (j/<!
         (eth-lib/contract-run
          (eth-lib/get-signer "http://127.0.0.1:8545"
                              (@! (nth env/+default-private-keys+ 9)))
          (@! *erc20-address*)
          (@! (get +dai-token-interface+ "abi"))
          "transfer"
          [(@! (deref #'+address+))
           "10000000000000"]
          {})))))

(fact:global
 {:setup [(do (l/rt:restart)
              (env/stop-ganache-server)
              (Thread/sleep 1000)
              (env/start-ganache-server
               ["ganache"
                "--gasLimit" "10000000000"
                "--chain.allowUnlimitedContractSize"
                "--wallet.seed" "test"
                "--host" "0.0.0.0"])
              (Thread/sleep 1000))
          (prep-dai)]
  :teardown [(l/rt:stop)]})

(defonce ^:dynamic *contract-address* nil)

(defn prep-contract
  []
  (do (s/rt:deploy t/+default-contract+)
      (def ^:dynamic *contract-address* (s/rt-get-contract-address))))

(defn create-vault
  [& [i]]
  (t/site:vault-create
   "T00001"
   "NBA/TESTNET"
   (nth env/+default-addresses+ (or i 1))
   200
   200000000
   100
   10000000000
   *erc20-address*))

(defn get-approved
  [i]
  (def +address+ (nth env/+default-addresses+ i))
  (j/<!
   (eth-lib/contract-run
    (eth-lib/new-rpc-provider "http://127.0.0.1:8545")
    (@! *erc20-address*)
    (@! (get +dai-token-interface+ "abi"))
    "allowed"
    [(@! (deref #'+address+))
     (@! (deref #'*contract-address*))]
    {})))

(defn approve-amount
  [i amount]
  (def +address+ (nth env/+default-addresses+ i))
  (def +private-key+   (nth env/+default-private-keys+ i))
  (def +amount+ amount)
  (j/<!
   (eth-lib/contract-run
    (eth-lib/get-signer "http://127.0.0.1:8545"
                        (@! (deref #'+private-key+)))
    (@! *erc20-address*)
    (@! (get +dai-token-interface+ "abi"))
    "approve"
    [(@! (deref #'*contract-address*))
     (@! (deref #'+amount+))]
    {})))

(defn add-account-amount
  [i vault-id amount]
  (s/with:contract-address [*contract-address*]
    (s/with:caller-private-key [(nth env/+default-private-keys+ i)]
      (t/user:account-deposit vault-id amount))))

(defn add-account
  [i vault-id amount]
  (approve-amount i amount)
  (add-account-amount i vault-id amount))



(comment

  ;; TEST

  (do (prep-dai)
      (prep-contract)
      (create-vault 9)
      (t/site:account-open
       "T00001"
       (nth env/+default-addresses+ 1))
      (approve-amount 1 20000000)
      (add-account-amount 1 "T00001" 200000)
      (s/with:params {:caller-private-key (nth env/+default-private-keys+ 1)}
        (t/user:withdraw-request "T00001" "WITHDRAW-1" 1000))))

^{:refer statspay.contract.vault-erc20/CANARY :adopt true :added "0.1"
  :setup [(prep-dai)
          (prep-contract)
          (create-vault 9)
          (t/site:account-open
           "T00001"
           (nth env/+default-addresses+ 1))
          (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
            (t/owner:account-open-confirm
             "T00001"
             (nth env/+default-addresses-raw+ 1)))
          (approve-amount 1 20000000)
          (add-account-amount 1 "T00001" 200000)]}
(fact "gets the message sender, for debugging"
  
  (token-fn "balanceOf" [(nth env/+default-addresses+ 1)])
  
  (token-fn "balanceOf" [*contract-address*])
  
  (token-fn "allowance" [(nth env/+default-addresses+ 1)
                         *contract-address*])
  
  => "19800000"

  (token-fn "balanceOf" [(nth env/+default-addresses+ 1)])
  
  (do 
    (s/with:params {:caller-private-key (nth env/+default-private-keys+ 1)}
      (t/user:withdraw-request "T00001" "WITHDRAW-1" 1000))
    (s/with:params {:caller-private-key (first env/+default-private-keys+)}
      (t/site:withdraw-confirm "T00001"
                               (nth env/+default-addresses+ 1)
                               "WITHDRAW-1"
                               1000))
    (s/with:params {:caller-private-key (nth env/+default-private-keys+ 9)}
      (t/owner:withdraw-approve "T00001"
                               (nth env/+default-addresses+ 1)
                               "WITHDRAW-1"
                               1000)))
  (token-fn "balanceOf" [*contract-address*])
  => "199000"
  
  (token-fn "balanceOf" [(nth env/+default-addresses+ 1)])
  => "9999999800980")

^{:refer statspay.contract.vault-erc20/ut:assert-admin :adopt true :added "0.1"
  :setup [(s/with:open-methods
           (prep-contract))]}
(fact "asserts that address is admin"
  ^:hidden []

  #_#_#_#_#_#_#_#_#_
  (s/with:open-methods
   (t/ut:assert-admin (nth env/+default-addresses+ 0)))
  => []

  (s/with:open-methods
   (t/ut:assert-admin (nth env/+default-addresses+ 1)))
  => h/wrapped?

  (s/with:open-methods
   (t/site:add-support (nth env/+default-addresses+ 1))
   (t/ut:assert-admin (nth env/+default-addresses+ 1)))
  => [])

^{:refer statspay.contract.vault-erc20/ut:assert-owner :adopt true :added "0.1"
  :setup [(s/with:open-methods
           (prep-contract)
           (create-vault 9))]}
(fact "asserts that address is vault owner"
  ^:hidden []

  #_#_#_#_#_#_
  (s/with:open-methods
   (t/ut:assert-owner "T00001"
                      (nth env/+default-addresses+ 1)))
  => h/wrapped?

  (s/with:open-methods
   (t/ut:assert-owner "T00001"
                      (nth env/+default-addresses+ 9)))
  => [])

^{:refer statspay.contract.vault-erc20/ut:assert-management :adopt true :added "0.1"
  :setup [(s/with:open-methods
           (prep-contract)
           (create-vault 9))]}
(fact "asserts that address is vault owner or site account"
  ^:hidden []

  #_#_#_#_#_#_#_#_#_
  (s/with:open-methods
   (t/ut:assert-management "T00001"
                           (nth env/+default-addresses+ 1)))
  => h/wrapped?

  (s/with:open-methods
   (t/ut:assert-management "T00001"
                           (nth env/+default-addresses+ 9)))
  => []

  (s/with:open-methods
   (t/ut:assert-management "T00001"
                           (nth env/+default-addresses+ 0)))
  => [])

^{:refer statspay.contract.vault-erc20/ut:get-account :adopt true :added "0.1"
  :setup [(s/with:open-methods
           (prep-contract)
           (create-vault 9)
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses+ 1)))]}
(fact "gets an account in the vault"
  ^:hidden []
  
  #_#_#_#_#_#_#_#_#_
  (s/with:open-methods
   (t/ut:get-account "T00001"
                     (nth env/+default-addresses+ 1)))
  => [1 false "" "0"]


  ;;
  ;; WRONG VAULT
  ;;
  (s/with:open-methods
   (t/ut:get-account "WRONGZ"
                     (nth env/+default-addresses+ 1)))
  => h/wrapped?
  
  ;;
  ;; WRONG ADDRESS
  ;;
  (s/with:open-methods
   (t/ut:get-account "T00001"
                     (nth env/+default-addresses+ 2)))
  => h/wrapped?)

^{:refer statspay.contract.vault-erc20/ut:get-withdraw :adopt true :added "0.1"
  :setup [(s/with:open-methods
           (do (prep-contract)
               (create-vault)
               (t/site:account-open
                "T00001"
                (nth env/+default-addresses+ 2))))
          (approve-amount 2 100000)
          (s/with:open-methods
           (do (add-account-amount 2 "T00001" 1000)
               (s/with:caller-private-key [(nth env/+default-private-keys+ 2)]
                 (t/user:withdraw-request "T00001"
                                          "WITHDRAW-1"
                                          100))))]}
(fact "gets a withdraw in the vault"
  ^:hidden []

  #_#_#_#_
  (s/with:open-methods
   (t/g:VaultLookup "T00001"))
  

  (s/with:open-methods
   (t/ut:get-withdraw "T00001"
                      (nth env/+default-addresses+ 2)
                      "WITHDRAW-1"))
  => ["100" 1])

^{:refer statspay.contract.vault-erc20/site:add-support :adopt true  :added "4.0"
  :setup [(s/with:params {:caller-private-key (nth env/+default-private-keys+ 9)}
            (s/with:measure
             (prep-contract)))]}
(fact "adds supporting keys to the contract"
  ^:hidden
  
  (s/with:measure
   (t/site:add-support (nth env/+default-addresses+ 0)))
  => (contains-in
      [(approx 0.06 0.04) h/wrapped?])
  
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 9)}
    (s/with:measure
     (t/site:add-support (nth env/+default-addresses+ 0))))
  => (contains-in
      [(approx 0.1 0.05) map?])

  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 9)}
    (s/with:measure
     (t/site:add-support (nth env/+default-addresses+ 1))))
  => (contains-in
      [(approx 0.1 0.05) map?])

  (s/with:params {:caller-private-key (first env/+default-private-keys+)}
    (s/with:measure
     (t/site:remove-support (nth env/+default-addresses+ 1))))
  => (contains-in
      [(approx 0.06 0.04) map?])
  
  (s/with:params {:caller-private-key (second env/+default-private-keys+)}
    (s/with:measure
     (t/site:remove-support (nth env/+default-addresses+ 0))))
  => (contains-in
      [(approx 0.06 0.04) h/wrapped?]))

^{:refer statspay.contract.vault-erc20/site:remove-support :adopt true :added "4.0"}
(fact "removes supporting keys from the contract")

^{:refer statspay.contract.vault-erc20/site:vault-create :adopt true :added "0.1"
  :setup [(s/with:measure
           (prep-contract))]}
(fact "creates a vault"
  ^:hidden

  (s/with:measure
   (t/site:vault-create
    "T00001"
    "NBA/TESTNET"
    (nth env/+default-addresses-raw+ 2)
    200
    20000000
    1000000
    10000000000
    *erc20-address*))
  => (contains-in
      [(approx 0.33 0.05)
       {"events" [{"args" ["vault_create" "T00001" (nth env/+default-addresses-raw+ 2)
                           "0"]
                   "event" "VaultEvent"}]}])

  (s/with:measure
   (t/site:vault-create
    "T00001"
    "NBA/TESTNET"
    (nth env/+default-addresses+ 2)
    200
    20000000
    1000000
    10000000000
    *erc20-address*))
  => (contains-in [(approx 0.04 0.01) h/wrapped?]))

^{:refer statspay.contract.vault-erc20/site:account-open :adopt true :added "0.1"
  :setup [(s/with:measure
           (prep-contract))
          (create-vault 9)]}
(fact "opens an account in a vault"
  ^:hidden

  (s/with:measure
   (t/site:account-open
    "T00001"
    (nth env/+default-addresses+ 1)))
  => (contains-in
      [(approx 0.09 0.02)
       {"events" [{"args" ["account_open" "T00001" (nth env/+default-addresses-raw+ 1)
                           "1"]
                   "event" "VaultEvent"}]}])
  

  (s/with:measure
   (t/site:account-open
    "T00001"
    (nth env/+default-addresses+ 2)))
  => (contains-in
      [(approx 0.09 0.02)
       {"events" [{"args" ["account_open" "T00001" (nth env/+default-addresses-raw+ 2)
                           "2"]
                   "event" "VaultEvent"}]}])
  
  (s/with:measure
   (t/site:account-open
    "T00001"
    (nth env/+default-addresses+ 1)))
  => (contains-in
      [(approx 0.04 0.02) h/wrapped?]))

^{:refer statspay.contract.vault-erc20/site:account-close :adopt true :added "0.1"
  :setup [(s/with:measure
           (prep-contract)
           (create-vault)
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses+ 1))
           (t/owner:account-open-confirm
            "T00001"
            (nth env/+default-addresses-raw+ 1))
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses+ 2))
           (t/owner:account-open-confirm
            "T00001"
            (nth env/+default-addresses-raw+ 2)))]}
(fact "closes an account in a vault"
  ^:hidden

  (s/with:measure
   (t/site:account-close
    "T00001"
    (nth env/+default-addresses+ 1)))
  => (contains-in
      [(approx 0.06 0.02)
       {"events" [{"event" "VaultEvent"
                   "args" ["account_close" "T00001" (nth env/+default-addresses-raw+ 1)
                           "1"]}]}])

  (s/with:measure
   (t/site:account-close
    "T00001"
    (nth env/+default-addresses+ 1)))
  => (contains-in
      [(approx 0.04 0.02) h/wrapped?]))

^{:refer statspay.contract.vault-erc20/owner:account-lock :adopt true :added "0.1"
  :setup [(s/with:measure
           (prep-contract)
           (create-vault 9)
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses+ 1))
           (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
             (t/owner:account-open-confirm
              "T00001"
              (nth env/+default-addresses-raw+ 1)))
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses+ 2))
           (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
             (t/owner:account-open-confirm
              "T00001"
              (nth env/+default-addresses-raw+ 2))))]}
(fact "locks the current account"
  ^:hidden

  ;;
  ;; ONLY VAULT MANAGEMENT
  ;;
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 3)}
    (s/with:measure
     (t/owner:account-lock "T00001" (nth env/+default-addresses+ 2))))
  => (contains-in
      [(approx 0.04 0.02) h/wrapped?])
  
  ;;
  ;; CORRECT
  ;;
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 9)}
    (s/with:measure
     (t/owner:account-lock "T00001" (nth env/+default-addresses+ 2))))
  => (contains-in
      [(approx 0.06 0.02)
       {"events" [{"args" ["account_lock" "T00001" (nth env/+default-addresses-raw+ 2)
                           "0"]
                   "event" "VaultEvent"}]}])
  
  ;;
  ;; CANNOT LOCK AGAIN
  ;;
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 9)}
    (s/with:measure
     (t/owner:account-lock "T00001" (nth env/+default-addresses+ 2))))
  => (contains-in
      [(approx 0.04 0.02) h/wrapped?])

  ;;
  ;; CANNOT LOCK NON ACCOUNT
  ;;
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 9)}
    (s/with:measure
     (t/owner:account-lock "T00001" (nth env/+default-addresses+ 5))))
  => (contains-in
      [(approx 0.04 0.02) h/wrapped?]))

^{:refer statspay.contract.vault-erc20/owner:account-unlock :adopt true :added "0.1"
  :setup [(s/with:measure
           (prep-contract)
           (create-vault 9)
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses+ 1))
           (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
             (t/owner:account-open-confirm
              "T00001"
              (nth env/+default-addresses-raw+ 1)))
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses+ 2))
           (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
             (t/owner:account-open-confirm
              "T00001"
              (nth env/+default-addresses-raw+ 2)))
           (s/with:params {:caller-private-key (nth env/+default-private-keys+ 9)}
             (t/owner:account-lock "T00001" (nth env/+default-addresses+ 2))))]}
(fact "unlocks the vault"
  ^:hidden

  ;;
  ;; ONLY VAULT MANAGEMENT
  ;;
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 3)}
    (s/with:measure
     (t/owner:account-unlock "T00001" (nth env/+default-addresses+ 2))))
  => (contains-in
      [(approx 0.04 0.02) h/wrapped?])

  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 9)}
    (s/with:measure
     (t/owner:account-unlock "T00001" (nth env/+default-addresses+ 2))))
  => (contains-in
      [(approx 0.06 0.02)
       {"events" [{"args" ["account_unlock" "T00001" (nth env/+default-addresses-raw+ 2)
                           "0"]
                   "event" "VaultEvent"}]}]))

^{:refer statspay.contract.vault-erc20/user:arbitration-vote :adopt true :added "0.1"
  :setup [(s/with:measure
           (prep-contract)
           (create-vault 9)
           (doseq [address (take 9 env/+default-addresses+)]
             (t/site:account-open
              "T00001"
              address)
             (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
             (t/owner:account-open-confirm
              "T00001"
              address))))]}
(fact "creates an arbitration vote"
  ^:hidden

  ;;
  ;; NON ACCOUNT HOLDERS ERROR
  ;;
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 9)}
    (s/with:measure
     (t/user:arbitration-vote "T00001")))
  => (contains-in
      [(approx 0.04 0.02) h/wrapped?])

  ;;
  ;; VOTES ONLY FROM ACCOUNT HOLDERS USERS
  ;;
  (s/with:params {:caller-private-key (first env/+default-private-keys+)}
    (s/with:measure
     (t/user:arbitration-vote "T00001")))
  => (contains-in
      [(approx 0.06 0.02)
       {"events" [{"args" ["arbitration_vote" "T00001" (first env/+default-addresses-raw+)
                           "1"] "event" "VaultEvent"}]}])

  [(s/with:params {:caller-private-key (second env/+default-private-keys+)}
     (t/user:arbitration-vote "T00001"))
   (s/with:params {:caller-private-key (nth env/+default-private-keys+ 2)}
     (t/user:arbitration-vote "T00001"))
   (s/with:params {:caller-private-key (nth env/+default-private-keys+ 3)}
     (t/user:arbitration-vote "T00001"))
   (t/g:VaultLookup "T00001")]
  => (contains-in
      [{"events" [{"args" ["arbitration_vote" "T00001" (second env/+default-addresses-raw+)
                           "2"] "event" "VaultEvent"}]}
       {"events" [{"args" ["arbitration_vote" "T00001" (nth env/+default-addresses-raw+ 2)
                           "3"] "event" "VaultEvent"}]}
       {"events" [{"args" ["arbitration_vote" "T00001" (nth env/+default-addresses-raw+ 3)
                           "4"] "event" "VaultEvent"}]}
       #"^1"])
  
  ;;
  ;; OVER THE THRESHOLD
  ;;
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 4)}
    (s/with:measure
     (t/user:arbitration-vote "T00001")))
  => (contains-in
      [(approx 0.07 0.02)
       {"events" [{"args" ["arbitration_vote" "T00001" (nth env/+default-addresses-raw+ 4)
                           "5"] "event" "VaultEvent"}]}])

  
  (t/g:VaultLookup "T00001")
  => #"^3")

^{:refer statspay.contract.vault-erc20/user:arbitration-unvote :adopt true :added "0.1"
  :setup [(s/with:measure
           (prep-contract)
           (create-vault 9)
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses+ 0))
           (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
             (t/owner:account-open-confirm
              "T00001"
              (nth env/+default-addresses-raw+ 0)))
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses+ 1))
           (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
             (t/owner:account-open-confirm
              "T00001"
              (nth env/+default-addresses-raw+ 1)))
           (s/with:params {:caller-private-key (first env/+default-private-keys+)}
             (t/user:arbitration-vote "T00001")))]}
(fact "unvotes on site arbitration"
  ^:hidden
  
  (s/with:params {:caller-private-key (first env/+default-private-keys+)}
    (s/with:measure
     (t/user:arbitration-unvote "T00001")))
  => (contains-in
      [(approx 0.07 0.02)
       {"events" [{"args" ["arbitration_unvote" "T00001" (first env/+default-addresses-raw+)
                           "0"]
                   "event" "VaultEvent"}]}]))

^{:refer statspay.contract.vault-erc20/user:withdraw-request :adopt true :added "0.1"
  :setup [(s/with:measure
           (prep-contract)
           (create-vault 9)
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses+ 1))
           (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
             (t/owner:account-open-confirm
              "T00001"
              (nth env/+default-addresses-raw+ 1)))
           (add-account 1 "T00001" 100000000))]}
(fact "requests a withdraw"
  ^:hidden

  (s/with:params {:caller-private-key (second env/+default-private-keys+)}
    (s/with:measure
     (t/user:withdraw-request "T00001"
                              "WITHDRAW-1"
                              10000)))
  => (contains-in
      [(approx 0.18 0.02)
       {"events" [{"args" ["withdraw_request" "WITHDRAW-1" (nth env/+default-addresses-raw+ 1)
                           "10000"]
                   "event" "VaultEvent"}]}])

  ;;
  ;; ERROR WHEN NO ACCOUNT
  ;;
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 2)}
    (s/with:measure
     (t/user:withdraw-request "T00001"
                              "WITHDRAW-2"
                              10000)))
  => (contains-in
      [(approx 0.07 0.02) h/wrapped?]))

^{:refer statspay.contract.vault-erc20/site:withdraw-confirm :adopt true :added "0.1"
  :setup [(s/with:measure
           (prep-contract)
           (create-vault 9)
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses+ 1))
           (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
             (t/owner:account-open-confirm
              "T00001"
              (nth env/+default-addresses-raw+ 1)))
           (add-account 1 "T00001" 100000000)
           (s/with:params {:caller-private-key (second env/+default-private-keys+)}
             (t/user:withdraw-request "T00001"
                                      "WITHDRAW-1"
                                      10000))
           (s/with:params {:caller-private-key (second env/+default-private-keys+)}
             (t/user:withdraw-request "T00001"
                                      "WITHDRAW-2"
                                      20000)))]}
(fact "confirms a withdraw"
  ^:hidden
  
  ;;
  ;; CONFIRM SUCCESS
  ;;
  (s/with:params {:caller-private-key (first env/+default-private-keys+)}
    (s/with:measure
     (t/site:withdraw-confirm "T00001"
                              (nth env/+default-addresses+ 1)
                              "WITHDRAW-1"
                              10000)))
  => (contains-in
      [(approx 0.06 0.02)
       {"events" [{"args" ["withdraw_confirm_site"
                           "WITHDRAW-1"
                           (nth env/+default-addresses-raw+ 1)
                           "10000"]
                   "event" "VaultEvent"}]}])
  
  ;;
  ;; CONFIRM AGAIN WILL ERROR
  ;;
  (s/with:params {:caller-private-key (first env/+default-private-keys+)}
    (s/with:measure
     (t/site:withdraw-confirm "T00001"
                              (nth env/+default-addresses+ 1)
                              "WITHDRAW-1"
                              10000)))
  => (contains-in
      [(approx 0.04 0.02) h/wrapped?])
  
  
  ;;
  ;; CONFIRM REQUIRES EXACT AMOUNT
  ;;
  (s/with:params {:caller-private-key (first env/+default-private-keys+)}
    (s/with:measure
     (t/site:withdraw-confirm "T00001"
                              (nth env/+default-addresses+ 1)
                              "WITHDRAW-2"
                              10000)))
  => (contains-in
      [(approx 0.04 0.02) h/wrapped?])

  
  ;;
  ;; CONFIRM REQUIRES SITE ADMIN
  ;;
  (s/with:params {:caller-private-key (second env/+default-private-keys+)}
    (s/with:measure
     (t/site:withdraw-confirm "T00001"
                              (nth env/+default-addresses+ 1)
                              "WITHDRAW-2"
                              20000)))
  => (contains-in
      [(approx 0.04 0.02) h/wrapped?]))

^{:refer statspay.contract.vault-erc20/site:withdraw-reject :adopt true :added "0.1"
  :setup [(s/with:measure
           (do (prep-contract)
               (create-vault 9)
               (t/site:account-open
                "T00001"
                (nth env/+default-addresses+ 1))
               (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
                 (t/owner:account-open-confirm
                  "T00001"
                  (nth env/+default-addresses-raw+ 1)))
               (add-account 1 "T00001" 100000000))
           (s/with:params {:caller-private-key (second env/+default-private-keys+)}
             (t/user:withdraw-request "T00001"
                                      "WITHDRAW-1"
                                      10000))
           (s/with:params {:caller-private-key (second env/+default-private-keys+)}
             (t/user:withdraw-request "T00001"
                                      "WITHDRAW-2"
                                      20000)))]}
(fact "rejects the withdraw"
  ^:hidden
  
  (s/with:params {:caller-private-key (first env/+default-private-keys+)}
    (s/with:measure
     (t/site:withdraw-reject "T00001"
                             (nth env/+default-addresses+ 1)
                             "WITHDRAW-1")))
  => (contains-in
      [(approx 0.06 0.02)
       {"events" [{"args" ["withdraw_reject_site" "WITHDRAW-1"
                           (nth env/+default-addresses-raw+ 1)
                           "10000"]
                   "event" "VaultEvent"}]}])
  
  ;;
  ;; CONFIRM AGAIN WILL ERROR
  ;;
  (s/with:params {:caller-private-key (first env/+default-private-keys+)}
    (s/with:measure
     (t/site:withdraw-reject "T00001"
                              (nth env/+default-addresses+ 1)
                              "WITHDRAW-1")))
  => (contains-in
      [(approx 0.04 0.02) h/wrapped?]))

^{:refer statspay.contract.vault-erc20/ut:withdraw-transfer :adopt true :added "0.1"}
(fact "helper function to facilitate transfer")

^{:refer statspay.contract.vault-erc20/owner:withdraw-approve :adopt true :added "0.1"
  :setup [(s/with:measure
           (prep-contract)
           (create-vault 9)
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses+ 1))
           (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
             (t/owner:account-open-confirm
              "T00001"
              (nth env/+default-addresses-raw+ 1)))
           (add-account 1 "T00001" 100000000)
           (s/with:params {:caller-private-key (second env/+default-private-keys+)}
             (t/user:withdraw-request "T00001"
                                      "WITHDRAW-1"
                                      10000))
           (s/with:params {:caller-private-key (first env/+default-private-keys+)}
             (t/site:withdraw-confirm "T00001"
                                      (nth env/+default-addresses+ 1)
                                      "WITHDRAW-1"
                                      10000)))]}
(fact "vault owner requires approval of withdraw"
  ^:hidden

  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 9)}
    (s/with:measure
     (t/owner:withdraw-approve "T00001"
                              (nth env/+default-addresses+ 1)
                              "WITHDRAW-1"
                              10000)))
  => (contains-in
      [number?
       {"events" [{"args" ["withdraw_transfer" "WITHDRAW-1"
                           (nth env/+default-addresses-raw+ 1)
                           "10000"]
                   "event" "VaultEvent"}]}])
  
  ;;
  ;; CONFIRM AGAIN WILL ERROR
  ;;
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 9)}
    (s/with:measure
     (t/owner:withdraw-approve "T00001"
                              (nth env/+default-addresses+ 1)
                              "WITHDRAW-1"
                              10000)))
  => (contains-in
      [number? h/wrapped?]))

^{:refer statspay.contract.vault-erc20/owner:withdraw-reject :adopt true :added "0.1"
  :setup [(s/with:measure
           (prep-contract)
           (create-vault 9)
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses+ 1))
           (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
             (t/owner:account-open-confirm
              "T00001"
              (nth env/+default-addresses-raw+ 1)))
           (add-account 1 "T00001" 100000000)

           (s/with:params {:caller-private-key (second env/+default-private-keys+)}
             (t/user:withdraw-request "T00001"
                                      "WITHDRAW-1"
                                      10000))
           (s/with:params {:caller-private-key (first env/+default-private-keys+)}
             (t/site:withdraw-confirm "T00001"
                                      (nth env/+default-addresses+ 1)
                                      "WITHDRAW-1"
                                      10000)))]}
(fact "vault owner can reject withdraw"
  ^:hidden

  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 9)}
    (s/with:measure
     (t/owner:withdraw-reject "T00001"
                              (nth env/+default-addresses+ 1)
                              "WITHDRAW-1")))
  => (contains-in
      [(approx 0.06 0.02)
       {"events" [{"args" ["withdraw_reject" "WITHDRAW-1"
                           (nth env/+default-addresses-raw+ 1)
                           "10000"] "event" "VaultEvent"}]}])
  
  ;;
  ;; REJECT AGAIN WILL ERROR
  ;;
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 9)}
    (s/with:measure
     (t/owner:withdraw-reject "T00001"
                              (nth env/+default-addresses+ 1)
                              "WITHDRAW-1")))
  => (contains-in
      [(approx 0.05 0.02) h/wrapped?]))

^{:refer statspay.contract.vault-erc20/user:withdraw-self :adopt true :added "0.1"
  :setup [(s/with:measure
           (do (prep-contract)
               (create-vault 9)
               (doseq [address (take 6 env/+default-addresses+)]
                 (t/site:account-open
                  "T00001"
                  address)
                 (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
                   (t/owner:account-open-confirm
                    "T00001"
                    address)))
               (add-account 2 "T00001" 100000000)
               (doseq [private-key (take 6 env/+default-private-keys+)]
                 (s/with:params {:caller-private-key private-key}
                   (t/user:arbitration-vote "T00001"))))
           (s/with:params {:caller-private-key (nth env/+default-private-keys+ 2)}
             (t/user:withdraw-request "T00001" "WITHDRAW-1" 1000000))
           (s/with:params {:caller-private-key (first env/+default-private-keys+)}
             (t/site:withdraw-confirm "T00001"
                                      (nth env/+default-addresses+ 2)
                                      "WITHDRAW-1"
                                      1000000)))]}
(fact "can withdraw only with site approval"
  ^:hidden

  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 2)}
    (s/with:measure
     (t/user:withdraw-self "T00001"
                                  "WITHDRAW-1")))
  => (contains-in
      [(approx 0.12 0.02)
       {"events" [{"args" ["withdraw_transfer" "WITHDRAW-1"
                           (nth env/+default-addresses-raw+ 2)
                           "1000000"]
                   "event" "VaultEvent"}]}]))

^{:refer statspay.contract.vault-erc20/user:account-deposit :adopt true :added "0.1"
  :setup [(s/with:measure
           (do (prep-contract)
               (create-vault 9)
               (t/site:account-open
                "T00001"
                (nth env/+default-addresses+ 2))
               (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
                 (t/owner:account-open-confirm
                  "T00001"
                  (nth env/+default-addresses-raw+ 2)))
               (approve-amount 2 10000000)))]}
(fact "adds to account"
  ^:hidden

  (s/with:caller-private-key [(nth env/+default-private-keys+ 2)]
    (s/with:measure
     (t/user:account-deposit "T00001" 10000000)))
  => (contains-in
      [(approx 0.13 0.02)
       {"events" [{"args" ["account_deposit" "T00001"
                           (nth env/+default-addresses-raw+ 2)
                           "10000000"]
                   "event" "VaultEvent"}]}]))
