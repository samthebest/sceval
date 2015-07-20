# sceval

ScEval, pronounced skev-al, is a highly Scalable evaluation framework using Spark for Scala

The intention is to PR this code it's way into MLLib, but it differs significantly from the MLLib API so I've dumped it here for people to review and comment.  I'm looking to get feedback so that when I do PR MLLib it will be accepted and won't be in vain.

## Dependencies

Has the following dependencies:

```
"org.apache.spark" % "spark-sql_2.10" % "1.3.0-cdh5.4.2",
"org.apache.spark" % "spark-core_2.10" % "1.3.0-cdh5.4.2",
"org.apache.spark" % "spark-mllib_2.10" % "1.3.0-cdh5.4.2"
```

If we added scalaz too then the code could be made more readible and terse, but for now just keeping dependencies to a minimum.

The unit tests are written in specs2.

## Why ScEval Over Native MLLib?

### (More) Invariant Binning in the Number of Partitions

The original MLLib implementation would bin the dataset quite differently for different numbers of partitions.  This has the following negatives:

1. Given a fixed test set, and a determinisitic model, the evaluation results could change in the number of partitions, which may seem odd.
2. Implementation details now effect logic
3. Harder to construct unit tests invariant of the number of partitions (this is elucidated by the fact that changing the number of partitions in the unit tests of the original MLLib code would break the unit tests)

### Binning is Uniform

For ScEval each bin will have an equal number of examples in it. This can be particularly important when performing k-fold x-validation since one will average across the bins.  When the number of records in each bin is not specified the average can not be trusted.  This can cause rather perculiar results, like non-monotonic ROC curves. As noted above changes in partitions will cause changes in binning, but also the MLLib implementation meant that changes in score distribution would also mean changes in binning.  To give an example why then such an implementation cannot be used for x-validation, let's use a contrived example.  Suppose fold 1 produces a model that has a score spike at 0.8, suppose this spike has 50% of the scores, suppose fold 2 produces a model that has a score spike at 0.7 again with 50% of the scores. Now when we average across this two models the spikes will by massively underweighted.  Of course the magnitude of under weighting has been designed in ths example to make a point, but nevetheless when one averages across bins one does need those bins to be of equal size.

(Note that the logic for uniform binning in a distributed context was quite tedious, all the magic happens in the `binnerFac` method in `BinUtils`)

### Thresholding is by Volume not by Score

**A very common problem in Data Science** is the **misconception** that a model score means more than it actually does, e.g. it represents a Measure, a 1D Norm or even a Probability. Moreover they are certainly not comparable across distinct models.  The only valid assumption is monotonicity, that is they induce an ordering relation, and in exceptional circumstances where the model is a bayes net one can consider scores as **estimations** of probabilities.  Even this is risky since the reliability of such estimates will be heavily determined by the complexity of the network, the number of examples and how the probabilities are estimated when training the model.  I have seen mathematically nonsensical papers going to great lengths to "normalize" model scores to probabilities using, to be frank, statistical hocus pocus, alas, I digress.

It can be considered possible to generate a mapping from model scores to probabilities assuming one has a representative sample of the population.  One simply bins by volume, uses the sample to determine the confusions, then uses the confusions to estimate probabilities either with confidence intervals, or using entropic estimation techniques.  Such a process I will likely add to ScEval in future.

So what ScEval does is use the ordering to sort the data, index it, then use those integers to bin uniformly, then these bins define the mechanism of thresholding that produces the confusions. I intend on adding a method to make it clearer how to enter the algorithm at this point if one knows what volume they with to threshold at.  Another benifit to logic that thresholds by volume is the user can more precisly specify how large their class will be irrespective of the distribution of the scores.

Some may find it perverse for an evaluation framework to put two examples with identical scores into seperate bins, especially when then later thresholding - "why did example A get chosen but not example B?".  Well the easiest counter to that is that scores in this situation are *represented* by floating-point numbers therefore they cannot be considered equal but only close, so provided closeness is preserved by the binning, which by definition it is, .  

Since most models in Machine Learning tend to be defined in terms of mathematical operations of real values, the model score is output as closest thing in computer science we have to real values - i.e. doubles.  It would be interesting, though unlikely to have many use-cases, to produce an implementation of an evaluation framework where the score type is generic with an `Ordering` type-class, then the user can choose to threshold by value.

### API Change

OOP -> Functional Pimp pattern, excessive encapsulation.

### Remark on Statistical Assumptions

One could argue that some of the above are largely unimportant since given sufficient examples these things average themselves out, and since it's assumed we are in a Big Data context worrying about these things is unnecessary.  This reasoning I fundementally disagree with, I would argue that when performing any experiment one should aim to minimize the number of assumptions and minimize the introduction of noise by the design of the experiment.  In this case the "design" of the experiment is the logic of the code, so the logic of the code should aim to minimize the introduction of noise.

One could argue that by repeating the experiement multiple times we ought to be able to observe some convergence or rely upon our measures of performance.


### Pedantic Mathematical Terminology

I'm a pure mathematician at heart and I prefer to avoid the term "metric" to mean anything other than a distance.

### Other Differences

Specs2 over 

SBT


## Contributing

If you wish to contribute please follow https://github.com/samthebest/dump/blob/master/convention-guidlines.md
