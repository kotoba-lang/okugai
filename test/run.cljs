#!/usr/bin/env nbb
;; nbb --classpath src:test test/run.cljs
(require '[clojure.test :as t] 'okugai.medium-test 'okugai.facts-test 'okugai.site-test)
(let [{:keys [fail error]} (t/run-tests 'okugai.medium-test 'okugai.facts-test 'okugai.site-test)]
  (js/process.exit (if (pos? (+ fail error)) 1 0)))
