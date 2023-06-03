(require 'cemerick.pomegranate.aether)
(require 'clojure.string)
(cemerick.pomegranate.aether/register-wagon-factory!
 "http" #(org.apache.maven.wagon.providers.http.HttpWagon.))
 
(defproject tahto/statstrade-pay-public "0.1.0"
  :description "Dual Custody Smart Contract Clearinghouse"
  :url "http://www.statstrade.io"
  :license {:name "MIT"
            :url "https://opensource.org/license/mit"}
  :aliases
  {"test-pay"   ["exec" "-ep"
                (clojure.string/join
                 " "
                 '[(use 'code.test)
                   (def res (run '[statspay]))
                   (System/exit (+ (:failed res) (:thrown res)))])]}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [foundation/code.test           "4.0.1"]
                 [foundation/code.manage         "4.0.1"]
                 [foundation/code.java           "4.0.1"]
                 [foundation/code.maven          "4.0.1"]
                 [foundation/code.doc            "4.0.1"]
                 [foundation/code.dev            "4.0.1"]
                 
                 [foundation/js.core             "4.0.1"]
                 [foundation/js.lib.driver       "4.0.1"]
                 [foundation/js.lib.ethereum     "4.0.1"]
                 
                 [foundation/jvm                 "4.0.1"]
                 [foundation/lib.docker          "4.0.1"]
                 [foundation/net.http            "4.0.1"]

                 [foundation/rt.basic            "4.0.1"]
                 [foundation/rt.solidity         "4.0.1"]

                 [foundation/std.lib             "4.0.1"]
                 [foundation/std.log             "4.0.1"]
                 [foundation/std.lang            "4.0.1"]
                 [foundation/std.text            "4.0.1"]
                 [foundation/xtalk.lang          "4.0.1"]
                 [foundation/xtalk.system        "4.0.1"]
                 
		 [org.xerial/sqlite-jdbc   "3.36.0.3"]]
  :profiles {:dev {:plugins [[lein-ancient "0.6.15"]
                             [lein-exec "0.3.7"]
                             [lein-cljfmt "0.7.0"]
                             [cider/cider-nrepl "0.25.11"]]}
             :repl {:injections [(try (require 'jvm.tool)
                                      (require '[std.lib :as h])
                                      (create-ns '.)
                                      (catch Throwable t (.printStackTrace t)))]}}

  :repositories [["clojars" "https://mirrors.ustc.edu.cn/clojars/"]]
  :resource-paths    ["resources" "src-build" "test-data"]
  :jvm-opts
  ["-Xms1536m"
   "-Xmx1536m"
   "-XX:MaxMetaspaceSize=1536m"
   "-XX:-OmitStackTraceInFastThrow"

   ;;
   ;; GC FLAGS
   ;;
   "-XX:+UseAdaptiveSizePolicy"
   "-XX:+AggressiveHeap"
   "-XX:+ExplicitGCInvokesConcurrent"
   "-XX:+UseCMSInitiatingOccupancyOnly"
   "-XX:+CMSClassUnloadingEnabled"
   "-XX:+CMSParallelRemarkEnabled"

   ;;
   ;; GC TUNING
   ;;   
   "-XX:MaxNewSize=256m"
   "-XX:NewSize=256m"
   "-XX:CMSInitiatingOccupancyFraction=60"
   "-XX:MaxTenuringThreshold=8"
   "-XX:SurvivorRatio=4"
   
   ;;
   ;; JVM
   ;;
   "-Djdk.tls.client.protocols=\"TLSv1,TLSv1.1,TLSv1.2\""
   "-Djdk.attach.allowAttachSelf=true"
   "--add-opens" "javafx.graphics/com.sun.javafx.util=ALL-UNNAMED"
   "--add-opens" "java.base/java.lang=ALL-UNNAMED"
   "--illegal-access=permit"])
