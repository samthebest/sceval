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

### Invariant Binning in the Number of Partitions

The original MLLib implementation would bin the dataset quite differently for different numbers of partitions.  This has the following negatives:

1. Given a fixed test set, and a determinisitic model, the evaluation results could change in the number of partitions, which may seem odd.
2. Implementation details now effect logic
3. Harder to construct unit tests invariant of the number of partitions (this is elucidated by the fact that changing the number of partitions in the unit tests of the original MLLib code would break the unit tests)

### Binning is Uniform

For ScEval each bin will have an equal number of examples in it. This can be particularly important when performing k-fold x-validation since one will average across the bins.  When the number of records in each bin is not specified the average can not be trusted.  This can cause rather perculiar results, like non-monotonic ROC curves. As noted above changes in partitions will cause changes in binning, but also the MLLib implementation meant that changes in score distribution would also mean changes in binning.  To give an example why then such an implementation cannot be used for x-validation, let's use a contrived example.  Suppose fold 1 produces a model that has a score spike at 0.8, suppose this spike has 50% of the scores, suppose fold 2 produces a model that has a score spike at 0.7 again with 50% of the scores. Now when we average across this two models the spikes will be massively underweighted.  Of course the magnitude of under weighting has been designed in ths example to make a point, nevetheless we will have an unnecessary loss in accuracy of averages.

(Note that the logic for uniform binning in a distributed context was a little tricky, all the magic happens in the `binnerFac` method in `BinUtils`)

### Thresholding is by Volume not by Score

**A very common problem in Data Science** is the **misconception** that a model score means more than it actually does, e.g. it represents a Measure, a 1D Norm or even a Probability. Moreover they are certainly not comparable across distinct models.  The only valid assumptions are monotonicity, that is they induce an ordering relation, a loose notion of topological structure as a limiting case and in exceptional circumstances where the model is a bayes net one can consider scores as **estimations** of probabilities.  Even this is risky since the reliability of such estimates will be heavily determined by the complexity of the network, the number of examples and how the input probabilities are estimated when training the model.  I have seen mathematically nonsensical papers going to great lengths to "normalize" model scores to probabilities using, to be frank, statistical hocus pocus, alas, I digress.

It can be considered possible to generate a mapping from model scores to probabilities assuming one has a representative sample of the population.  One simply bins by volume, uses the sample to determine the confusions, then uses the confusions to estimate probabilities either with confidence intervals, or using entropic estimation techniques.  Such a process I will likely add to ScEval in future.

So what ScEval does is use the ordering to sort the data, index it, then use those integers to bin uniformly, then these bins define the mechanism of thresholding that produces the confusions. I intend on adding a method to make it clearer how to enter the algorithm at this point if one knows what volume they with to threshold at.  Another benifit to logic that thresholds by volume is the user can more precisly specify how large their class will be irrespective of the distribution of the scores.

Some may find it perverse for an evaluation framework to put two examples with identical scores into seperate bins, especially when then later thresholding - "why did example A get chosen but not example B?".  Well the scores in this situation are *represented* by floating-point numbers therefore they cannot be considered equal but only close, again this comes back to misconceptions around mathematical **meaning**.  As a limiting case the thresholding does preserve this closeness and so preserves the topological structure of the scores - in fact by collapsing equal *representations* of scores that may technically be different no such homeomorphism will exist at the limiting case for score identifying implementations.

In summary thresholding by volume over score has the following benfits:

1. Allows for the aforementioned equal binning that allows averaging in k-fold x-validation
2. Allows for more presice control over volume when thresholding
3. Mathematicaly correct
4. Does not introduce any assumptions about the model

#### Future Addition Regarding Thresholding

Since most models in Machine Learning tend to be defined in terms of mathematical operations of real values, the model score is output as closest thing in computer science we have to real values - i.e. doubles.  It would be interesting, though unlikely to have many use-cases, to produce an implementation of an evaluation framework where the score type is a generic `T <: Equals : Ordering`, i.e. extends `Equals` and has an `Ordering` type-class. Then, and only then, the user can choose to threshold by value.

### Remark on Statistical Assumptions

One could argue that some of the above are unimportant since with sufficient examples these things average themselves out, and since it's assumed we are in a Big Data context worrying about these things is unnecessary.  This reasoning I fundementally disagree with, I would argue that when performing any experiment one should aim to minimize the number of assumptions and minimize the introduction of noise by the design of the experiment.  In this case the "design" of the experiment is the logic of the code, so the logic of the code should aim to minimize the introduction of noise.

One could argue that by repeating the experiement multiple times we ought to be able to observe some convergence or rely upon our measures of performance if they do not seem to vary.  Indeed this is true, but in order to reach "statistical confidence" the experiment may need to run many times, and since we are in a Big Data context, this is undesirable.

Finally as hinted at earlier, one can use the same logic to estimate probabilities, which may then be used by some downstream model/analytics.  The increased reliability of this implementation means that it is useful not only in evaluation, but probability estimation.

### API Change

The API is somewhat more functional where we use static methods and the Pimp my Library Pattern over OOP & encapsulation.  Furthermore the code style is a little more functional and less imperative.

### Similtaneous Multi-Model Evaluation Support

Using ScEval one can similtaneously evaluate multiple models. For example, suppose we are an advertiser with 100 tags, we can similtanesouly evaluate all 100 models associated with those tags, that is the number of spark stages is fixed.  We need not run the evaluation 100 times in sequence producing 100 times as many spark stages/jobs.  The other obvious use case is k-fold x-validation.

### Deprecation of AUC & F1-Score (and no MCC (Mathews Correlation Coefficient))

A bit like attributing greater semantics to a model score than it actually has, an equally common mistake is attributing greater semantics to the rather odd magic numbers seen in Data Science, that is AUC, F1-score & MCC.  The motivation for such numbers is obvious, given a difficult to comprehend set of performance measures on a model how do we compare models to ultimately decide which is the "best".  The correct answer is you don't, you use your use case with a probabilistic measure to decide. Different use cases will warrent different thresholds and different measures, for example recall may be important for medical diagnosis, while precision may be important for fraud detection.  In many circumstance, like marketting and advertising it's possible to go straight to expected profit - now this really *does have meaning*. 

So when writting an evaluation API I feel responsible to include some warning regarding the mathematical validity of any methods that lack foundation.

#### AUC

AUC's only probabilistic meaning is it's the probability that a classifier will rank a randomly chosen positive instance higher than a randomly chosen negative one (assuming 'positive' ranks higher than 'negative').

The fundemental issue with AUC is that a threshold has not been chosen and nearly all use cases of Machine Learning require such a process to *make a decision*.  End "users" of Machine Learning models rarely see a "rank", and if they do, they certainly do not see the rank of every single possible example.  The *action* is often at the upper end of a model's ranks while AUC takes into account the whole range.  Users do not want to see a part of an advert*, perhaps greyed out according to it's rank, nor do they care about ranks of items on the 10th page of a search.  A patient doesn't want to know how they are ranked in terms of getting cancer, they want to know if another test is needed - or the probability they have cancer.

There are also some easily googlable papers around AUC instability and such and such, but really just some common sense and basic logic is sufficient to disregard this measure as a **fashion** not something of real meaning.  It is used because other people use it, not because it actually means anything.

*Well, most don't want to see any adverts, but let's ignore that detail.

#### F1-Score & MCC

These measures (as with many correlation coefficients) only have Probabilistic or Information Theoretic meaning if we assume we have a can opener, I mean, if we assume some underlying distributions in our data.  The motivation being to combine precision and recall, or a confusion, into a single number.  This seems to be an unheathly obsession of the human race.  Perhaps we are incabable of coping with two or more numbers.  Please let's try to keep Data Science a Science and accept that the world is not as simple as we would like it to be. We must design a measure of performance based on each individual use case, even if that means we actually have to think every so often.

### Terminology

(As the reader ought to be able to tell) I'm a pure mathematician at heart, so the use of the term "metric" has been removed - the use of the term should reserved to mean a "distance".

### Other Differences

Specs2 is used over ScalaTest, this is really out of familiarity, especially when used in conjunction with ScalaCheck.  If merged into MLLib, I imagine the tests would need to be refactored to use ScalaTest since most devmasters don't like multiple frameworks in a single project.

SBT is used over Maven, no reason other than convention and familarity.

Property based tests are used, which can make test times slower.  It may be desirable to have environment dependent configurations so that larger numbers of permutations are attempted inside a CI pipeline rather than locally.

## Contributing

If you wish to contribute please follow https://github.com/samthebest/dump/blob/master/convention-guidlines.md
