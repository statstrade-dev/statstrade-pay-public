(ns statspay.contract.vault-native-test
  (:use code.test)
  (:require [std.lang :as l]
            [std.lib :as h]
            [std.string :as str]
            [rt.solidity]
            [statspay.common.env-ganache :as env]))

(l/script- :solidity
  {:runtime :web3
   :require [[rt.solidity :as s]
             [statspay.contract.vault-native :as t]]})

(fact:global
 {:setup [(do (l/rt:restart)
              (env/stop-ganache-server)
              (env/start-ganache-server
               ["ganache"
                "--gasLimit" "10000000000"
                "--chain.allowUnlimitedContractSize"
                "--wallet.seed" "test"
                "--host" "0.0.0.0"]))]
  :teardown [(l/rt:stop)]})

(defonce ^:dynamic *contract-address* nil)

(comment
  (h/port:check-available 8545)
  (h/port:get-available [8545])
  
  (h/port:check-available 8080)
  )

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
   10000000000))

(defn add-account
  [i vault-id amount]
  (s/with:contract-address [*contract-address*]
    (s/with:caller-private-key [(nth env/+default-private-keys+ i)]
      (s/with:caller-payment [amount]
        (t/user:account-deposit vault-id)))))

^{:refer statspay.contract.vault-native/CANARY :adopt true :added "0.1"
  :setup [(prep-contract)
          (create-vault 9)
          (t/site:account-open
           "T00001"
           (nth env/+default-addresses-raw+ 1))
          (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
            (t/owner:account-open-confirm
             "T00001"
             (nth env/+default-addresses-raw+ 1)))
          (add-account 1 "T00001" 200000)]}
(fact "gets the message sender, for debugging"

  
  (str/split
   (s/with:contract-address [*contract-address*]
     (t/g:VaultLookup "T00001"))
   #",")
  => (contains-in
      ["1" "NBA/TESTNET" string?
       "200000" "0" "1" "0" "200" "200000000" "100" "10000000000"])
  
  (s/rt:node-get-balance *contract-address*)
  => 200000
  

  (do 
    (s/with:params {:caller-private-key (nth env/+default-private-keys+ 1)}
      (t/user:withdraw-request "T00001" "WITHDRAW-1" 1000))
    (s/with:params {:caller-private-key (nth env/+default-private-keys+ 0)}
      (t/site:withdraw-confirm "T00001"
                               (nth env/+default-addresses-raw+ 1)
                               "WITHDRAW-1"
                               1000))
    (s/with:params {:caller-private-key (nth env/+default-private-keys+ 9)}
      (t/owner:withdraw-approve "T00001"
                               (nth env/+default-addresses-raw+ 1)
                               "WITHDRAW-1"
                               1000)))
  
  (s/rt:node-get-balance *contract-address*)
  => 199000

  (s/rt:node-get-balance (nth env/+default-addresses-raw+ 1))
  => number?)

^{:refer statspay.contract.vault-native/user:withdraw-request.multiple :added "0.1" :adopt true
  :setup [(s/with:measure
           (prep-contract)
           (create-vault 9)
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses-raw+ 1))
           (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
             (t/owner:account-open-confirm
              "T00001"
              (nth env/+default-addresses-raw+ 1)))
           (add-account 1 "T00001" 100000000)
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses-raw+ 2))
           (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
             (t/owner:account-open-confirm
              "T00001"
              (nth env/+default-addresses-raw+ 2)))
           (add-account 2 "T00001"  50000000)
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses-raw+ 3))
           (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
             (t/owner:account-open-confirm
              "T00001"
              (nth env/+default-addresses-raw+ 3)))
           (add-account 3 "T00001"  30000000))]}
(fact "requests a withdraw"
  ^:hidden

  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 1)}
    (s/with:measure
     (t/user:withdraw-request "T00001"
                              "WITHDRAW-1"
                              10000)))
  => (contains-in
      [(approx 0.20 0.04)
       {"events" [{"args" ["withdraw_request" "WITHDRAW-1" (nth env/+default-addresses-raw+ 1)
                           "10000"]
                   "event" "VaultEvent"}]}])

  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 2)}
    (s/with:measure
     (t/user:withdraw-request "T00001"
                              "WITHDRAW-2"
                              20000)))

  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 3)}
    (s/with:measure
     (t/user:withdraw-request "T00001"
                              "WITHDRAW-3"
                              30000)))

  (t/g:VaultLookup "T00001"))

^{:refer statspay.contract.vault-native/ut:assert-admin :added "0.1"
  :setup [(s/with:open-methods
           (prep-contract))]}
(fact "asserts that address is admin"
  ^:hidden

  (s/with:open-methods
   (t/ut:assert-admin (first env/+default-addresses-raw+)))
  => []

  (s/with:open-methods
   (t/ut:assert-admin (nth env/+default-addresses-raw+ 1)))
  => h/wrapped?

  (s/with:open-methods
   (t/site:add-support (nth env/+default-addresses-raw+ 1))
   (t/ut:assert-admin (nth env/+default-addresses-raw+ 1)))
  => [])

^{:refer statspay.contract.vault-native/ut:assert-owner :added "0.1"
  :setup [(s/with:open-methods
           (prep-contract)
           (create-vault 9))]}
(fact "asserts that address is vault owner"
  ^:hidden

  (s/with:open-methods
   (t/ut:assert-owner "T00001"
                      (nth env/+default-addresses-raw+ 1)))
  => h/wrapped?

  (s/with:open-methods
   (t/ut:assert-owner "T00001"
                      (nth env/+default-addresses-raw+ 9)))
  => [])

^{:refer statspay.contract.vault-native/ut:assert-management :added "0.1"
  :setup [(s/with:open-methods
           (prep-contract)
           (create-vault 9))]}
(fact "asserts that address is vault owner or site account"
  ^:hidden

  (s/with:open-methods
   (t/ut:assert-management "T00001"
                           (nth env/+default-addresses-raw+ 1)))
  => h/wrapped?

  (s/with:open-methods
   (t/ut:assert-management "T00001"
                           (nth env/+default-addresses-raw+ 9)))
  => []

  (s/with:open-methods
   (t/ut:assert-management "T00001"
                           (first env/+default-addresses-raw+)))
  => [])

^{:refer statspay.contract.vault-native/ut:get-account :added "0.1"
  :setup [(s/with:open-methods
           (prep-contract)
           (create-vault 9)
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses-raw+ 1)))]}
(fact "gets an account in the vault"
  ^:hidden

  (s/with:open-methods
   (t/ut:get-account "T00001"
                     (nth env/+default-addresses-raw+ 1)))
  => [1 false "" "0"]


  ;;
  ;; WRONG VAULT
  ;;
  (s/with:open-methods
   (t/ut:get-account "WRONGZ"
                     (nth env/+default-addresses-raw+ 1)))
  => h/wrapped?
  
  ;;
  ;; WRONG ADDRESS
  ;;
  (s/with:open-methods
   (t/ut:get-account "T00001"
                     (nth env/+default-addresses-raw+ 2)))
  => h/wrapped?)

^{:refer statspay.contract.vault-native/ut:get-withdraw :added "0.1"
  :setup [(s/with:open-methods
           (do (prep-contract)
               (create-vault)
               (t/site:account-open
                "T00001"
                (nth env/+default-addresses-raw+ 2))
               (t/owner:account-open-confirm
                "T00001"
                (nth env/+default-addresses-raw+ 2))))
          
          (s/with:open-methods
           (do (add-account 2 "T00001" 1000)
               (s/with:caller-private-key [(nth env/+default-private-keys+ 2)]
                 (t/user:withdraw-request "T00001"
                                          "WITHDRAW-1"
                                          100))))]}
(fact "gets a withdraw in the vault"
  ^:hidden

  (s/with:open-methods
   (t/g:VaultLookup "T00001"))
  

  #_#_#_
  (s/with:open-methods
   (t/ut:get-withdraw "T00001"
                      (nth env/+default-addresses-raw+ 2)
                      "WITHDRAW-1"))
  => ["100" 1])

^{:refer statspay.contract.vault-native/site:change-authority :added "0.1"}
(fact "changes the site authority")

^{:refer statspay.contract.vault-native/site:add-support :added "4.0"
  :setup [(s/with:params {:caller-private-key (nth env/+default-private-keys+ 9)}
            (s/with:measure
             (prep-contract)))]}
(fact "adds supporting keys to the contract"
  ^:hidden
  
  (s/with:measure
   (t/site:add-support (first env/+default-addresses-raw+)))
  => (contains-in
      [(approx 0.06 0.04) h/wrapped?])
  
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 9)}
    (s/with:measure
     (t/site:add-support (first env/+default-addresses-raw+))))
  => (contains-in
      [(approx 0.1 0.05) map?])

  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 9)}
    (s/with:measure
     (t/site:add-support (nth env/+default-addresses-raw+ 1))))
  => (contains-in
      [(approx 0.1 0.05) map?])

  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 0)}
    (s/with:measure
     (t/site:remove-support (nth env/+default-addresses-raw+ 1))))
  => (contains-in
      [(approx 0.06 0.04) map?])
  
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 1)}
    (s/with:measure
     (t/site:remove-support (first env/+default-addresses-raw+))))
  => (contains-in
      [(approx 0.06 0.04) h/wrapped?]))

^{:refer statspay.contract.vault-native/site:remove-support :added "4.0"}
(fact "removes supporting keys from the contract")

^{:refer statspay.contract.vault-native/site:vault-create :added "0.1"
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
    10000000000))
  => (contains-in
      [(approx 0.33 0.05)
       {"events" [{"args" ["vault_create" "T00001" (nth env/+default-addresses-raw+ 2)
                           "0"]
                   "event" "VaultEvent"}]}])

  (s/with:measure
   (t/site:vault-create
    "T00001"
    "NBA/TESTNET"
    (nth env/+default-addresses-raw+ 2)
    200
    20000000
    1000000
    10000000000))
  => (contains-in [(approx 0.04 0.01) h/wrapped?]))

^{:refer statspay.contract.vault-native/site:account-open :added "0.1"
  :setup [(s/with:measure
           (prep-contract))
          (create-vault 9)]}
(fact "opens an account in a vault"
  ^:hidden

  (s/with:measure
   (t/site:account-open
    "T00001"
    (nth env/+default-addresses-raw+ 1)))
  => (contains-in
      [(approx 0.09 0.02)
       {"events" [{"args" ["account_open" "T00001" (nth env/+default-addresses-raw+ 1)
                           "1"]
                   "event" "VaultEvent"}]}])
  

  (s/with:measure
   (t/site:account-open
    "T00001"
    (nth env/+default-addresses-raw+ 2)))
  => (contains-in
      [(approx 0.09 0.02)
       {"events" [{"args" ["account_open" "T00001" (nth env/+default-addresses-raw+ 2)
                           "2"]
                   "event" "VaultEvent"}]}])
  
  (s/with:measure
   (t/site:account-open
    "T00001"
    (nth env/+default-addresses-raw+ 1)))
  => (contains-in
      [(approx 0.04 0.02) h/wrapped?]))

^{:refer statspay.contract.vault-native/site:account-close :added "0.1"
  :setup [(s/with:measure
           (prep-contract)
           (create-vault)
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses-raw+ 1))
           (s/with:caller-private-key [(nth env/+default-private-keys+ 1)]
             (t/owner:account-open-confirm
              "T00001"
              (nth env/+default-addresses-raw+ 1)))
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses-raw+ 2)))]}
(fact "closes an account in a vault"
  ^:hidden

  (s/with:measure
   (t/site:account-close
    "T00001"
    (nth env/+default-addresses-raw+ 1)))
  => (contains-in
      [(approx 0.06 0.02)
       {"events" [{"event" "VaultEvent"
                   "args" ["account_close" "T00001" (nth env/+default-addresses-raw+ 1)
                           "1"]}]}])

  (s/with:measure
   (t/site:account-close
    "T00001"
    (nth env/+default-addresses-raw+ 1)))
  => (contains-in
      [(approx 0.04 0.02) h/wrapped?]))

^{:refer statspay.contract.vault-native/owner:set-auto :added "0.1"}
(fact "auto approval from owner")

^{:refer statspay.contract.vault-native/owner:account-open-confirm :added "0.1"}
(fact "confirms the account using room owner key")

^{:refer statspay.contract.vault-native/owner:account-lock :added "0.1"
  :setup [(s/with:measure
           (prep-contract)
           (create-vault 9)
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses-raw+ 1))
           (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
             (t/owner:account-open-confirm
              "T00001"
              (nth env/+default-addresses-raw+ 1)))
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses-raw+ 2))
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
     (t/owner:account-lock "T00001" (nth env/+default-addresses-raw+ 2))))
  => (contains-in
      [(approx 0.04 0.02) h/wrapped?])
  
  ;;
  ;; CORRECT
  ;;
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 9)}
    (s/with:measure
     (t/owner:account-lock "T00001" (nth env/+default-addresses-raw+ 2))))
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
     (t/owner:account-lock "T00001" (nth env/+default-addresses-raw+ 2))))
  => (contains-in
      [(approx 0.04 0.02) h/wrapped?])

  ;;
  ;; CANNOT LOCK NON ACCOUNT
  ;;
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 9)}
    (s/with:measure
     (t/owner:account-lock "T00001" (nth env/+default-addresses-raw+ 5))))
  => (contains-in
      [(approx 0.04 0.02) h/wrapped?]))

^{:refer statspay.contract.vault-native/owner:account-unlock :added "0.1"
  :setup [(s/with:measure
           (prep-contract)
           (create-vault 9)
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses-raw+ 1))
           (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
             (t/owner:account-open-confirm
                "T00001"
                (nth env/+default-addresses-raw+ 1)))
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses-raw+ 2))
           (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
             (t/owner:account-open-confirm
              "T00001"
              (nth env/+default-addresses-raw+ 2)))
           (s/with:params {:caller-private-key (nth env/+default-private-keys+ 9)}
             (t/owner:account-lock "T00001" (nth env/+default-addresses-raw+ 2))))]}
(fact "unlocks the vault"
  ^:hidden

  ;;
  ;; ONLY VAULT MANAGEMENT
  ;;
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 3)}
    (s/with:measure
     (t/owner:account-unlock "T00001" (nth env/+default-addresses-raw+ 2))))
  => (contains-in
      [(approx 0.04 0.02) h/wrapped?])

  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 9)}
    (s/with:measure
     (t/owner:account-unlock "T00001" (nth env/+default-addresses-raw+ 2))))
  => (contains-in
      [(approx 0.06 0.02)
       {"events" [{"args" ["account_unlock" "T00001" (nth env/+default-addresses-raw+ 2)
                           "0"]
                   "event" "VaultEvent"}]}]))

^{:refer statspay.contract.vault-native/user:arbitration-vote :added "0.1"
  :setup [(s/with:measure
           (prep-contract)
           (create-vault 9)
           (doseq [address (take 9 env/+default-addresses-raw+)]
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
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 0)}
    (s/with:measure
     (t/user:arbitration-vote "T00001")))
  => (contains-in
      [(approx 0.06 0.02)
       {"events" [{"args" ["arbitration_vote"
                           "T00001"
                           (first env/+default-addresses-raw+)
                           "1"] "event" "VaultEvent"}]}])

  [(s/with:params {:caller-private-key (nth env/+default-private-keys+ 1)}
     (t/user:arbitration-vote "T00001"))
   (s/with:params {:caller-private-key (nth env/+default-private-keys+ 2)}
     (t/user:arbitration-vote "T00001"))
   (s/with:params {:caller-private-key (nth env/+default-private-keys+ 3)}
     (t/user:arbitration-vote "T00001"))
   (t/g:VaultLookup "T00001")]
  => (contains-in
      [{"events" [{"args" ["arbitration_vote" "T00001" (nth env/+default-addresses-raw+ 1)
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
      [(approx 0.06 0.02)
       {"events" [{"args" ["arbitration_vote" "T00001" (nth env/+default-addresses-raw+ 4)
                           "5"] "event" "VaultEvent"}]}])

  
  (t/g:VaultLookup "T00001")
  => #"^3")

^{:refer statspay.contract.vault-native/user:arbitration-unvote :added "0.1"
  :setup [(s/with:measure
           (prep-contract)
           (create-vault 9)
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses-raw+ 0))
           (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
             (t/owner:account-open-confirm
              "T00001"
              (nth env/+default-addresses-raw+ 0)))
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses-raw+ 2))
           (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
             (t/owner:account-open-confirm
              "T00001"
              (nth env/+default-addresses-raw+ 2)))
           (s/with:params {:caller-private-key (nth env/+default-private-keys+ 0)}
             (t/user:arbitration-vote "T00001")))]}
(fact "unvotes on site arbitration"
  ^:hidden
  
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 0)}
    (s/with:measure
     (t/user:arbitration-unvote "T00001")))
  => (contains-in
      [(approx 0.07 0.02)
       {"events" [{"args" ["arbitration_unvote" "T00001" (first env/+default-addresses-raw+)
                           "0"]
                   "event" "VaultEvent"}]}]))

^{:refer statspay.contract.vault-native/user:withdraw-request :added "0.1"
  :setup [(s/with:measure
           (prep-contract)
           (create-vault 9)
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses-raw+ 1))
           (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
             (t/owner:account-open-confirm
              "T00001"
              (nth env/+default-addresses-raw+ 1)))
           (add-account 1 "T00001" 100000000))]}
(fact "requests a withdraw"
  ^:hidden

  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 1)}
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

^{:refer statspay.contract.vault-native/site:withdraw-confirm :added "0.1"
  :setup [(s/with:measure
           (prep-contract)
           (create-vault 9)
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses-raw+ 1))
           (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
             (t/owner:account-open-confirm
              "T00001"
              (nth env/+default-addresses-raw+ 1)))
           (add-account 1 "T00001" 100000000)
           (s/with:params {:caller-private-key (nth env/+default-private-keys+ 1)}
             (t/user:withdraw-request "T00001"
                                      "WITHDRAW-1"
                                      10000))
           (s/with:params {:caller-private-key (nth env/+default-private-keys+ 1)}
             (t/user:withdraw-request "T00001"
                                      "WITHDRAW-2"
                                      20000)))]}
(fact "confirms a withdraw"
  ^:hidden

  (comment
    (t/g:VaultWithdrawals
     "T00001"
     (nth env/+default-addresses-raw+ 1)
     "WITHDRAW-2"))
  
  ;;
  ;; CONFIRM SUCCESS
  ;;
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 0)}
    (s/with:measure
     (t/site:withdraw-confirm "T00001"
                              (nth env/+default-addresses-raw+ 1)
                              "WITHDRAW-1"
                              10000)))
  => (contains-in
      [(approx 0.06 0.02)
       {"events" [{"args" ["withdraw_confirm_site"
                           "WITHDRAW-1"
                           (nth env/+default-addresses-raw+ 1)
                           ]
                   "event" "VaultEvent"}]}])
  
  ;;
  ;; CONFIRM AGAIN WILL ERROR
  ;;
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 0)}
    (s/with:measure
     (t/site:withdraw-confirm "T00001"
                              (nth env/+default-addresses-raw+ 1)
                              "WITHDRAW-1"
                              10000)))
  => (contains-in
      [(approx 0.04 0.02) h/wrapped?])
  
  
  ;;
  ;; CONFIRM REQUIRES EXACT AMOUNT
  ;;
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 0)}
    (s/with:measure
     (t/site:withdraw-confirm "T00001"
                              (nth env/+default-addresses-raw+ 1)
                              "WITHDRAW-2"
                              10000)))
  => (contains-in
      [(approx 0.04 0.02) h/wrapped?])

  
  ;;
  ;; CONFIRM REQUIRES SITE ADMIN
  ;;
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 1)}
    (s/with:measure
     (t/site:withdraw-confirm "T00001"
                              (nth env/+default-addresses-raw+ 1)
                              "WITHDRAW-2"
                              20000)))
  => (contains-in
      [(approx 0.04 0.02) h/wrapped?]))

^{:refer statspay.contract.vault-native/site:withdraw-reject :added "0.1"
  :setup [(s/with:measure
           (do (prep-contract)
               (create-vault 9)
               (t/site:account-open
                "T00001"
                (nth env/+default-addresses-raw+ 1))
               (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
                 (t/owner:account-open-confirm
                  "T00001"
                  (nth env/+default-addresses-raw+ 1)))
               (add-account 1 "T00001" 100000000))
           (s/with:params {:caller-private-key (nth env/+default-private-keys+ 1)}
             (t/user:withdraw-request "T00001"
                                      "WITHDRAW-1"
                                      10000))
           (s/with:params {:caller-private-key (nth env/+default-private-keys+ 1)}
             (t/user:withdraw-request "T00001"
                                      "WITHDRAW-2"
                                      20000)))]}
(fact "rejects the withdraw"
  ^:hidden
  
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 0)}
    (s/with:measure
     (t/site:withdraw-reject "T00001"
                             (nth env/+default-addresses-raw+ 1)
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
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 0)}
    (s/with:measure
     (t/site:withdraw-reject "T00001"
                              (nth env/+default-addresses-raw+ 1)
                              "WITHDRAW-1")))
  => (contains-in
      [(approx 0.04 0.02) h/wrapped?]))

^{:refer statspay.contract.vault-native/ut:withdraw-transfer :added "0.1"}
(fact "helper function to facilitate transfer")

^{:refer statspay.contract.vault-native/owner:withdraw-approve :added "0.1"
  :setup [(s/with:measure
           (prep-contract)
           (create-vault 9)
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses-raw+ 1))
           (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
             (t/owner:account-open-confirm
              "T00001"
              (nth env/+default-addresses-raw+ 1)))
           (add-account 1 "T00001" 100000000)
           (s/with:params {:caller-private-key (nth env/+default-private-keys+ 1)}
             (t/user:withdraw-request "T00001"
                                      "WITHDRAW-1"
                                      10000))
           (s/with:params {:caller-private-key (nth env/+default-private-keys+ 0)}
             (t/site:withdraw-confirm "T00001"
                                      (nth env/+default-addresses-raw+ 1)
                                      "WITHDRAW-1"
                                      10000)))]}
(fact "vault owner requires approval of withdraw"
  ^:hidden
  
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 9)}
    (s/with:measure
     (t/owner:withdraw-approve "T00001"
                              (nth env/+default-addresses-raw+ 1)
                              "WITHDRAW-1"
                              10000)))
  => (contains-in
      [number?
       {"events" [{"args" ["withdraw_transfer" "WITHDRAW-1"
                           (nth env/+default-addresses-raw+ 1)
                           "10000"] "event" "VaultEvent"}]}])
  
  ;;
  ;; CONFIRM AGAIN WILL ERROR
  ;;
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 9)}
    (s/with:measure
     (t/owner:withdraw-approve "T00001"
                              (nth env/+default-addresses-raw+ 1)
                              "WITHDRAW-1"
                              10000)))
  => (contains-in
      [number?
       h/wrapped?]))

^{:refer statspay.contract.vault-native/owner:withdraw-reject :added "0.1"
  :setup [(s/with:measure
           (prep-contract)
           (create-vault 9)
           (t/site:account-open
            "T00001"
            (nth env/+default-addresses-raw+ 1))
           (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
             (t/owner:account-open-confirm
                  "T00001"
                  (nth env/+default-addresses-raw+ 1)))
           (add-account 1 "T00001" 100000000)

           (s/with:params {:caller-private-key (nth env/+default-private-keys+ 1)}
             (t/user:withdraw-request "T00001"
                                      "WITHDRAW-1"
                                      10000))
           (s/with:params {:caller-private-key (nth env/+default-private-keys+ 0)}
             (t/site:withdraw-confirm "T00001"
                                      (nth env/+default-addresses-raw+ 1)
                                      "WITHDRAW-1"
                                      10000)))]}
(fact "vault owner can reject withdraw"
  ^:hidden

  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 9)}
    (s/with:measure
     (t/owner:withdraw-reject "T00001"
                              (nth env/+default-addresses-raw+ 1)
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
                              (nth env/+default-addresses-raw+ 1)
                              "WITHDRAW-1")))
  => (contains-in
      [(approx 0.05 0.02) h/wrapped?]))

^{:refer statspay.contract.vault-native/user:withdraw-self :added "0.1"
  :setup [(s/with:measure
           (do (prep-contract)
               (create-vault 9)
               (doseq [address (take 6 env/+default-addresses-raw+)]
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
           (s/with:params {:caller-private-key (nth env/+default-private-keys+ 0)}
             (t/site:withdraw-confirm "T00001"
                                      (nth env/+default-addresses-raw+ 2)
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

^{:refer statspay.contract.vault-native/user:account-deposit :added "0.1"
  :setup [(s/with:measure
           (do (prep-contract)
               (create-vault 9)
               (t/site:account-open
                "T00001"
                (nth env/+default-addresses-raw+ 2))
               (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
                 (t/owner:account-open-confirm
                  "T00001"
                  (nth env/+default-addresses-raw+ 2)))))]}
(fact "adds to account"
  ^:hidden

  (s/with:caller-private-key [(nth env/+default-private-keys+ 2)]
    (s/with:caller-payment [10000000]
      (s/with:measure
       (t/user:account-deposit "T00001"))))
  => (contains-in
      [(approx 0.06 0.04)
       {"events" [{"args" ["account_deposit"
                           "T00001"
                           (nth env/+default-addresses-raw+ 2)
                           "10000000"]
                   "event" "VaultEvent"}]}]))
