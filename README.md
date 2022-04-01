## GoodGuesser: Sprinkle some Machine Learning Magic on top of your regular code!

![alt text](https://github.com/drcode/good-guesser/blob/master/good_guesser_logo.png?raw=true)

GoodGuesser can approximate a single numerical value using arbitrary input data, given human-labeled examples. It also requires you to provide one or more functions that can generate numbers from the input data that correlate with the desired answer. It then uses multiple linear regressions to estimate a value for new inputs.

## Usage

The easiest way to learn how to use GoodGuesser is to watch my video: https://www.youtube.com/watch?v=2SFNbiurWhc

First, require the good-guesser function:


```
(ns foo.foo
   (:require [good-guesser.good-guesser :refer [good-guesser]]))
```

Then, simply call `good-guesser` and pass in an arbitrary keyword name, the input data, and one or more function names:

```
(defn num-squares [bmp]
  (pprint bmp)
  (good-guesser :num-squares bmp count-pixels concave-pixels))
```

The input data can be of any clojure type or structure- Keep in mind it should be reasonable in size, as it will be stored long term and printed. If your input data is too big for this, you should first think about how to slim it down. Note that you will need to be able to create human-labeled examples by inspecting the input data. (If this is difficult in your case, there is a flag I'll discuss later called `:visualizer` that may help.)

The expectation of good-guesser is that it will be call many times with the same keyword name (likely from only a single line of code in your program). Then, look for the `.gg` file created for your program, using the keyword name. This will contain up to 20 guesses that good-guesser has seen. If it sees additional examples it will ignore them.

In the `.gg` file, replace guesses shown with human labeled values, based on your own human input.

Now, if you rerun the orginal function that uses good-guesser with new input, it will estimate a decent answer for you.

Note that GoodGuesser does basic things to try to maintain decent performance:

1. GoodGuesser will only reload the gg file if it has been modified
1. GoodGuesser will only recalculate the regression on first run, or if the file has changed, or if the calling parameters have changed, or if the `:actual-value` flag has been specified and a new guess needs to be made.

You can get the raw parameters of the linear regression by calling good-guesser with the `:verbose` flag:

```
  (good-guesser :num-squares bmp count-pixels concave-pixels :verbose true)
```

Other supported flags:

- `visualizer`: Sometimes, when you have input data, it is in a format that makes human labeling difficult. Because of this you can add an additional multiline comment that will appear in the gg file with each example, to aid the human that needs to label the examples. You must supply a function which takes the input data, as well as the output guess as parameters and then prints out extra useful data for the human labeler that they can reference in the gg file.
- `actual-value`: Lets you supply the expected correct output value- In this case good-guesser simply returns this value and adds the example to the gg file as a "human-labeled example". This function is useful for running validation tests, where a correct answer is know ahead of time.
- `preview`: When true, runs good-guesser in a nondestructive mode, i.e. without modifying the gg file. Mainly for debugging the good-guesser library, itself.

##License

Distributed under the Eclipse Public License 1.0
