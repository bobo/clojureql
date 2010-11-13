(ns clojureql.core-test
  (:use [clojureql core internal predicates]
        clojure.test))

(deftest sql-compilation
  (testing "Simple selects"
    (are [x y] (= x y)
         (-> (table {} :users [:*]) sql)
         "SELECT users.* FROM users"
         (-> (table {} :users [:id :name]) sql)
         "SELECT users.id,users.name FROM users"
         (-> (table {} :users [:avg/wage]) sql)
         "SELECT avg(users.wage) FROM users"
         (-> (table {} :users [[:avg/wage :as :avg]]) sql)
         "SELECT avg(users.wage) AS avg FROM users"))
  (testing "Where predicates"
    (are [x y] (= x y)
         (-> (table {} :users [:id])
                    (select (where (= :id 5)))
                    sql)
         "SELECT users.id FROM users WHERE (id = 5)"
         (-> (table {} :users [:id])
             (select (where (or (= :id 5) (>= :id 10))))
             sql)
         "SELECT users.id FROM users WHERE ((id = 5) OR (id >= 10))"
         (-> (table {} :users [:id])
             (select (where (and (= :id 5) (>= :id 10))))
             sql)
         "SELECT users.id FROM users WHERE ((id = 5) AND (id >= 10))"
         (-> (table {} :users [:id])
             (select (where (and (= :id 5) (or (>= :id 10)
                                               (<= :id 20)))))
                    sql)
         "SELECT users.id FROM users WHERE ((id = 5) AND ((id >= 10) OR (id <= 20)))"
         (-> (table {} :users [:id])
             (select (where (and (!= :id 5) (or (> :id 10)
                                                (< :id 20)))))
             sql)
         "SELECT users.id FROM users WHERE ((id != 5) AND ((id > 10) OR (id < 20)))"))
  (testing "Projections"
    (are [x y] (= x y)
         (-> (table {} :users [:id])
             (project #{:name :title})
             sql)
         "SELECT users.id,users.name,users.title FROM users"))
  (testing "Joins"
    (are [x y] (= x y)
         (-> (table {} :users [:id])
                    (join (table {} :salary [:wage]) :id)
                    sql)
         "SELECT users.id,salary.wage FROM users JOIN salary USING(id)"
         (-> (table {} :users [:id])
                    (join (table {} :salary [:wage]) (where (= :user.id :salary.id)))
                    sql)
         "SELECT users.id,salary.wage FROM users JOIN salary ON (user.id = salary.id)"))
  (testing "Renaming in joins"
    (are [x y] (= x y)
         (-> (table {} :users [:id])
                    (join (table {} :salary [:wage]) (where (= :user.id :salary.id)))
                    (rename {:id :idx}) ; TODO: This should only work with fully qualified names
                    sql)
         "SELECT users.id,salary.wage FROM users AS users(idx) JOIN salary ON (user.id = salary.id)"))
                                        ; TODO: Shouldn't this be ON (users.idx = salary.id) ?
  (testing "Aggregate functions"
    (are [x y] (= x y)
         (-> (table {} :users)
             (select (where (= :admin true)))
             (aggregate [:count/* :avg/wage])
             sql)
         "SELECT count(users.*),avg(users.wage) FROM users WHERE (admin = true)"
         (-> (table {} :users)
             (select (where (= :admin true)))
             (aggregate [:count/*, "corr(x,y)"] [:country :city])
             sql)
         "SELECT users.country,users.city,count(users.*),corr(x,y) FROM users WHERE (admin = true)  GROUP BY users.country,users.city"
         (-> (table {} :users)
             (select (where (= :admin true)))
             (aggregate [:count/*, :corr/x:y] [:country :city])
             sql)
         "SELECT users.country,users.city,count(users.*),corr(users.x,users.y) FROM users WHERE (admin = true)  GROUP BY users.country,users.city"))
  (testing "Table aliases"
    (are [x y] (= x y)
        (let [u1 (table {} {:users :u1} [:id :article :price])
              w1 (table {} {:salary :w1})]
          (sql (join u1 w1 (where (= :u1.id :w1.id)))))
        "SELECT u1.id,u1.article,u1.price FROM users u1 JOIN salary w1 ON (u1.id = w1.id)"
        (let [u1 (table {} {:users :u1} [:id :article :price])
              w1 (table {} {:salary :w1} [])]
          (-> (join u1 w1 (where (= :u1.id :w1.id)))
              (select (where (= :s2.article "NULL")))
              sql))
        (str "SELECT u1.id,u1.article,u1.price FROM users u1 "
             "JOIN salary w1 ON (u1.id = w1.id) WHERE (s2.article = NULL)")))
)