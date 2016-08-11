Grothendieck
============
The aim of this project is to develop a language which understands abstract mathematics like abstract algebra, algebraic topology and W-star Calculus. The foundations are heirarchically
 builds the abstract concepts based purely on category-theoretic language.
 
"The purpose of abstraction is not to be vague, but to create a new semantic level in which one can be absolutely precise" - Edsger Dijkstra

Abstract Math Layer: The Core
=============================

Category Theory lies at the core of Grothendieck. Category theory formalizes mathematical structure and its concepts in terms of a collection of objects and of arrows (also called morphisms). 
A category has two basic properties: the ability to compose the arrows associatively and the existence of an identity arrow for each object. Many areas of mathematics can be formalised by 
category theory as categories. Hence category theory uses abstraction to make it possible to state and prove many intricate and subtle mathematical results in these fields in a much simpler 
way. This idea thus can be used to hierarchically define and classify most branches of mathematics. Thus this would allow theories to be defined using as few assumptions as possible.

In Grothendieck we implement the following core concepts:

Category Theory
Abstract Algebra
Algebraic Topology
W-star Calculus

Forge-Delite-LMS
================

We accomplish multi-architecture support, by defining Numerical Linear Algebra in Grothendieck on top of Delite, which is a meta-DSL. Forge is a prototype meta-DSL that generates Delite DSL implementations from a specification-like program. Delite itself is based on LMS which is a framework for Runtime Code Generation and Compiled DSLs.

This allows us to generate Scala code for prototyping along with C++, CUDA, OpenCL code to exploit CPU and GPU architectures.

1) Forge: https://github.com/stanford-ppl/Forge

2) Delite: https://github.com/stanford-ppl/Delite

3) LMS: https://github.com/TiarkRompf/virtualization-lms-core


Applied Math Layer
==================

One we have all the abstract mathematical foundation taken care of, we develop the Applied Math layer using the same ideology. Since the core of the language is extremely expressive and fully optimised, we only need to think about the algorithms and not their implementation details. We develop the following theories in this layer:

Graph Theory and Data Structures
Probability Theory
Convex Optimisation: Proximal Type Algorithms
Stochastic Optimisation: Stochastic Gradient Descent, Expectation Propagation, Sampling Methods
Ordinary/Partial Differential Equations
Tensor Network Theory



Installation
============

    cd Grothendieck
    sbt compile

Updating Existing Repo to Latest Commits
============

    cd Grothendieck
    git pull
    sbt compile

Environment Variables
============
sbt and several other scripts require the following environment variables to be set:

    HYPER_HOME: hyperdsl repository home directory
    LMS_HOME: virtualization-lms-core repository home directory
    DELITE_HOME: Delite repository home directory
    FORGE_HOME: Forge repository home directory
    JAVA_HOME: JDK home directory

init-env.sh contains the sensible defaults for all of these paths except JAVA_HOME
