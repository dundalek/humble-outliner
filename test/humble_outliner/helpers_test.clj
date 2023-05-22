(ns humble-outliner.helpers-test
  (:require
   [clojure.test :refer [are deftest is]]
   [humble-outliner.helpers :refer [to-compact to-compact-by-text from-compact from-compact-by-text update-compact]]))

(deftest to-compact-test
  (is (= [4 5 6]
         (to-compact {6 {:order 2}
                      5 {:order 1}
                      4 {:order 0}})))
  (is (= [1
          2 [21
             22 [221]]
          3]
         (to-compact {1 {:order 0}
                      2 {:order 1}
                      21 {:order 0 :parent 2}
                      22 {:order 1 :parent 2}
                      221 {:order 0 :parent 22}
                      3 {:order 2}})))
  (is (= ["four" "five" "six"]
         (to-compact-by-text {6 {:order 2 :text "six"}
                              5 {:order 1 :text "five"}
                              4 {:order 0 :text "four"}}))))

(deftest from-compact-test
  (is (= {6 {:order 2}
          5 {:order 1}
          4 {:order 0}}
         (from-compact [4 5 6])))

  (is (= {1 {:order 0}
          2 {:order 1}
          21 {:order 0 :parent 2}
          22 {:order 1 :parent 2}
          221 {:order 0 :parent 22}
          3 {:order 2}}
         (from-compact [1
                        2 [21
                           22 [221]]
                        3])))
  (is (= {"four" {:order 0 :text "four"}
          "five" {:order 1 :text "five"}
          "six" {:order 2 :text "six"}}
         (from-compact-by-text ["four" "five" "six"]))))

(deftest update-compact-test
  (is (= [1 2]
         (update-compact [1 2 3] #(dissoc % 3)))))

(deftest compact-round-trip
  (are [compact] (= compact (update-compact compact identity))
    []

    [1 2 3]

    [1
     2 [21
        22 [221]]
     3])

  (are [items] (= items (to-compact-by-text (from-compact-by-text items)))
    []

    ["four" "five" "six"]

    ["1"
     "2" ["21"
          "22" ["221"]]
     "3"]))
