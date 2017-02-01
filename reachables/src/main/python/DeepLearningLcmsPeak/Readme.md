### 20n Deep Learning LCMS

The files associated with this package contain different algorithms for analyzing and "calling"
(Identifying) peaks in an LCMS trace.

### 1) Algorithms
## 1.0 Bucketed Peak Detection Algorithm
To Run: python bucketed_deep.py -h

What this does: Divides the entire LCMS trace into a large, 2D grid and
detects "peaks" based on the 2D grid and surrounding area.
Outputs a list of tuples, containing (Mz, Retention Time, Absolute Max Intensity).

## 1.1 Bucketed Differential Peak Algorithm
To Run: python bucketed_differential_deep.py -h

What this does: Detects peaks as described in section 1.0, but does this across two conditions
(And possibly replicates of a given condition) to find where peaks differ significantly.
Peaks that are truly differential indicate a difference in chemical profile of two sets of samples.

## 1.2 Dynamic Differential Peak Algorithm
To Run: python bucketed_differential_deep.py -h

What this does: This algorithm individually detects peaks based on slope changes in the m/z axis without
higher values nearby.  It then uses an alignment algorithm to rapidly align the peaks between replicates
based on closeness in the M/Z and RT domains (Dropping any peaks that don't have replicate support) and then again
between samples, finally resulting in a differential peak profile based on the groups of aligned peaks between samples.

### 2) Python Support
Python versions 2.7 and above should work.  The installation file installs the needed dependencies for python 2.7.

### 3) Installation

## 3.0 Anaconda
The easiest way to install the needed dependencies is using Anaconda.
`conda create -n new environment --file conda_req.txt`

## 3.1 Manually
You can also individually install dependencies.  The primary dependencies are as follows:
```
netCDF4
Pandas
Numpy
Tqdm
Keras (As well as Theano or Tensorflow based on your backend)
Sklearn
```