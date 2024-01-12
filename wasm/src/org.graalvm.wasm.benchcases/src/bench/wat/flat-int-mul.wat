;;
;; Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
;; DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
;;
;; The Universal Permissive License (UPL), Version 1.0
;;
;; Subject to the condition set forth below, permission is hereby granted to any
;; person obtaining a copy of this software, associated documentation and/or
;; data (collectively the "Software"), free of charge and under any and all
;; copyright rights in the Software, and any and all patent rights owned or
;; freely licensable by each licensor hereunder covering either (i) the
;; unmodified Software as contributed to or provided by such licensor, or (ii)
;; the Larger Works (as defined below), to deal in both
;;
;; (a) the Software, and
;;
;; (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
;; one is included with the Software each a "Larger Work" to which the Software
;; is contributed by such licensors),
;;
;; without restriction, including without limitation the rights to copy, create
;; derivative works of, display, perform, and distribute the Software and make,
;; use, sell, offer for sale, import, export, have made, and have sold the
;; Software and the Larger Work(s), and to sublicense the foregoing rights on
;; either these or other terms.
;;
;; This license is subject to the following condition:
;;
;; The above copyright notice and either this complete permission notice or at a
;; minimum a reference to the UPL must be included in all copies or substantial
;; portions of the Software.
;;
;; THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
;; IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
;; FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
;; AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
;; LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
;; OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
;; SOFTWARE.
;;
(module
  (type $int_func (func (result i32)))
  (type $proc (func))
  (type $teardown_func (func (param i32)))

  (global $iterations i32 (i32.const 10000000))

  (memory $memory (export "memory") 0)

  (func (export "benchmarkSetupEach") (type $proc))

  (func (export "benchmarkTeardownEach") (type $teardown_func))

  (func (export "benchmarkRun") (type $int_func)
    (local $i i32)
    (local $x i32)
    (local $y i32)
    (local $z i32)
    (local $w i32)
    (local.set $x (i32.const 1))
    (local.set $y (i32.const 1))
    (local.set $z (i32.const 1))
    (local.set $w (i32.const 1))

    (loop $bench_loop
      ;; Perform four int multiplications
      (local.set $x (i32.mul (local.get $x) (i32.const 3)))
      (local.set $y (i32.mul (local.get $y) (i32.const 5)))
      (local.set $z (i32.mul (local.get $z) (i32.const 7)))
      (local.set $w (i32.mul (local.get $w) (i32.const 11)))

      ;; Increment loop counter and exit loop
      (local.set $i (i32.add (local.get $i) (i32.const 1)))
      (br_if $bench_loop (i32.lt_s (local.get $i) (global.get $iterations)))
    )

    (i32.and (i32.and (i32.ne (local.get $x) (i32.const 0)) (i32.ne (local.get $y) (i32.const 0))) (i32.and (i32.ne (local.get $z) (i32.const 0)) (i32.ne (local.get $w) (i32.const 0))))
  )
)
