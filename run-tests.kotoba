;; Run the gamblingfacilityops actor test suite under nbb.
;;
;;   nbb --classpath src:test run-tests.cljs
;;
;; Equivalent to `clojure -M:test`. Before 2026-07-25 neither existed: the
;; :test alias set only :extra-paths, so nothing invoked the suite.
(require '[gamblingfacilityops.test :as t])

(apply t/-main [])
