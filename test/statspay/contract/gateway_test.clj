(ns statspay.contract.gateway-test
  (:use code.test)
  (:require [std.lang :as l]
            [std.lib :as h]
            [rt.solidity :as s]
            [statspay.common.env-ganache :as env]))

(l/script- :solidity
  {:runtime :web3
   :require [[rt.solidity :as s]
             [statspay.contract.gateway :as g]]})

(fact:global
 {:setup [(do (l/rt:restart)
              (env/stop-ganache-server)
              (env/start-ganache-server))]
  :teardown [(l/rt:stop)]})

^{:refer statspay.contract.gateway/ut:assert-admin :added "0.1"}
(fact "asserts that user is an admin")

^{:refer statspay.contract.gateway/site:change-authority :added "0.1"
  :setup [(s/with:caller-private-key [(first env/+default-private-keys+)]
            (s/rt:deploy g/+default-contract+))]}
(fact "changes the site authority address")

^{:refer statspay.contract.gateway/site:add-support :added "0.1"}
(fact "adds site support to contract")

^{:refer statspay.contract.gateway/site:remove-support :added "0.1"}
(fact "removes site support from contract")

^{:refer statspay.contract.gateway/site:set-paymaster :added "0.1"
  :setup [(s/with:caller-private-key [(first env/+default-private-keys+)]
            (s/rt:deploy g/+default-contract+))]}
(fact "sets a paymaster"
  ^:hidden
  
  (s/with:measure
   (g/site:set-paymaster "hello"
                         (second env/+default-addresses+)))

  (s/with:measure
   (g/site:set-paymaster "hello"
                         (nth env/+default-addresses+ 2)))
  => (contains-in
      [(approx 0.07 0.04)
       map?])

  (g/g:SitePaymasters "hello")
  => "0xB0d070485a1bfb782A5B1B9F095A48CCF20F11A6")

^{:refer statspay.contract.gateway/site:del-paymaster :added "0.1"}
(fact "deletes a paymaster"
  ^:hidden
  
  (s/with:measure
   (g/site:del-paymaster "hello"))
  => (contains-in
      [(approx 0.04 0.02)
       map?])

  (g/g:SitePaymasters "hello")
  => "0x0000000000000000000000000000000000000000")

^{:refer statspay.contract.gateway/payment-native :added "0.1"
  :setup [(s/with:measure
           (s/with:caller-private-key [(first env/+default-private-keys+)]
             (s/rt:deploy g/+default-contract+)))]}
(fact "makes a payment"
  ^:hidden
  
  (s/with:params {:caller-private-key (nth env/+default-private-keys+ 9)
                  :caller-payment 10000000}
    (s/with:measure
     (g/payment-native "HELLO")))
  => (contains-in
      [(approx 0.07 0.02)
       {"events" [{"args" ["payment_native" "HELLO" (nth env/+default-addresses-raw+ 9)
                           "10000000"],
                   "event" "GatewayEvent"}]}])
  
  (s/with:params {:caller-private-key (second env/+default-private-keys+)
                  :caller-payment 10000000}
    (s/with:measure
     (g/payment-native "HELLO")))
  => (contains-in
      [(approx 0.07 0.02)
       {"events" [{"args" ["payment_native" "HELLO" (nth env/+default-addresses-raw+ 1)
                           "10000000"],
                   "event" "GatewayEvent"}]}]))
