(ns statspay.common.base-gen
  (:require [std.lang :as l]
            [std.lib :as h]))

(defn tmpl-function
  [entry]
  (let [opts  (h/template-meta)
        {:strs [name inputs
                stateMutability]} entry
        {:keys [top abi provider signer contract-address]
         :or {abi '-/ABI}} opts
        sym  (clojure.core/symbol name)
        args (vec (map-indexed (fn [i {:strs [name]}]
                                 (if (empty? name)
                                   (or (get top i)
                                       (h/error "Top Input required: "
                                                {:entry entry
                                                 :inputs inputs
                                                 :name name
                                                 :top top
                                                 :index i}))
                                   (symbol name)))
                               inputs))]
    (h/$
     (defn.js ~sym
       [~@args opts signer contract-address]
       (return
        (. (js.lib.eth-lib/contract-run (or signer
                                            (~signer)
                                            (~provider))
                                        (or contract-address
                                            (~contract-address))
                                        ~abi
                                        ~name
                                        ~args
                                        (or opts {}))
           (then (fn:> [res]
                   (:? (and (k/obj? res)
                            (. res wait))
                       (. res (wait))
                       res)))))))))
