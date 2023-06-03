(ns statspay.contract.council-test
  (:use code.test)
  (:require [std.lang :as l]
            [std.lib :as h]
            [std.string :as str]
            [rt.solidity :as s]
            [statspay.common.env-ganache :as env]))

(l/script- :solidity
  {:runtime :web3
   :require [[rt.solidity :as s]
             [statspay.contract.council :as council]
             [statspay.common.eth-test-erc20 :as source]]})

(defonce ^:dynamic *contract-address* nil)
(defonce ^:dynamic *erc20-address* nil)

(defn prep-erc20
  []
  (do (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
        (s/rt:deploy source/+default-contract+))
      (def ^:dynamic *erc20-address* (s/rt-get-contract-address))
      (doseq [address (take 9 env/+default-addresses+)]
        (s/with:contract-address [@#'*erc20-address*]
          (s/with:caller-private-key [(nth env/+default-private-keys+ 9)]
            (source/transfer address "10000000000000"))))))

(defn transfer-amount
  [amount]
  (let [contract-address (s/rt-get-contract-address)]
    (s/with:contract-address [*erc20-address*]
      (source/transfer contract-address amount))))

(fact:global
 {:setup [(do (l/rt:restart)
              (env/stop-ganache-server)
              (env/start-ganache-server)
              (prep-erc20))]
  :teardown [(l/rt:stop)]})

^{:refer statspay.contract.council/ut:get-quorum :added "0.1"}
(fact "gets the required number of votes for a verdict")

^{:refer statspay.contract.council/ut:get-index :added "0.1"
  :setup [(s/with:open-methods
           (s/rt:deploy council/+default-contract+))]}
(fact "gets the current array index"
  ^:hidden
  
  (s/with:open-methods
   (council/ut:get-index (first env/+default-addresses-raw+)))
  => 1)

^{:refer statspay.contract.council/ut:get-remove :added "0.1"
  :setup [(s/with:open-methods
           (s/rt:deploy council/+default-contract+))]}
(fact "removes from array"
  ^:hidden
  
  (s/with:open-methods
   (council/ut:get-remove (first env/+default-addresses-raw+))
   (council/ut:get-index (first env/+default-addresses-raw+)))
  => 0)

^{:refer statspay.contract.council/ut:get-add :added "0.1"
  :setup [(s/with:open-methods
           (s/rt:deploy council/+default-contract+))]}
(fact "adds to array"
  ^:hidden

  (s/with:open-methods
   (council/ut:get-add (second env/+default-addresses-raw+))
   (council/ut:get-index (second env/+default-addresses-raw+)))
  => 2)

^{:refer statspay.contract.council/action:member-take-profit :added "0.1"
  :setup [(s/rt:deploy council/+default-contract+)
          (transfer-amount 10000000)
          (council/member:proposal-create
           "A"
           1
           (second env/+default-addresses-raw+))
          (council/member:proposal-process "A")]}
(fact "takes profit for erc20 tokens"
  ^:hidden
  
  (council/member:proposal-create
   "C"
   0
   *erc20-address*)
  
  (s/with:caller-private-key [(second env/+default-private-keys+)]
    (council/member:proposal-vote
     "C"
     0))
  (def +balance-old+
    (s/with:contract-address [*erc20-address*]
      (source/balanceOf (second env/+default-addresses+))))
  
  (def +out+
    (council/member:proposal-process "C"))

  +out+
  => map?
  
  (- (s/with:contract-address [*erc20-address*]
          (source/balanceOf (second env/+default-addresses+)))
     +balance-old+)
  => 5000000)

^{:refer statspay.contract.council/action:member-add :added "0.1"}
(fact "adds a member to the council")

^{:refer statspay.contract.council/action:member-remove :added "0.1"}
(fact "removes a member to the council")

^{:refer statspay.contract.council/member:proposal-create :added "0.1"
  :setup [(s/rt:deploy council/+default-contract+)]}
(fact "creates a proposal"
  ^:hidden

  (council/member:proposal-create
   "A"
   1
   (second env/+default-addresses-raw+))

  (str/split
   (council/g:ProposalLookup "A")
   #",")
  => (contains-in
      ["1" "1" string? "1" "0" "0" "1"])
  

  (council/member:proposal-process "A")
  => map?

  (council/g:MemberArray 1)
  => (second env/+default-addresses-raw+)

  (do (council/member:proposal-create
       "B"
       1
       (nth env/+default-addresses-raw+ 2))
      
      (council/member:proposal-process "B"))
  => h/wrapped?)

^{:refer statspay.contract.council/member:proposal-vote :added "0.1"
  :setup [(s/rt:deploy council/+default-contract+)
          (council/member:proposal-create
           "A"
           1
           (second env/+default-addresses-raw+))
          (council/member:proposal-process "A")]}
(fact "votes for a proposal"
  ^:hidden

  (s/with:caller-private-key [(second env/+default-private-keys+)]
    (council/member:proposal-create
     "B"
     1
     (nth env/+default-addresses-raw+ 2)))
  
  (council/member:proposal-vote
   "B"
   0)
  => map?

  (s/with:caller-private-key [(second env/+default-private-keys+)]
    (council/member:proposal-process "B"))
  => map?)

^{:refer statspay.contract.council/member:proposal-cancel :added "0.1"
  :setup [(s/rt:deploy council/+default-contract+)
          (council/member:proposal-create
           "A"
           1
           (second env/+default-addresses-raw+))]}
(fact "cancels a proposal"
  ^:hidden
  
  (council/member:proposal-cancel
   "A")
  => map?
  
  (council/member:proposal-process "A")
  => h/wrapped?)

^{:refer statspay.contract.council/member:proposal-process :added "0.1"}
(fact "processes the proposal is quorum is reached")
