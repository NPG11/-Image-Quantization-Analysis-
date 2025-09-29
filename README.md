Overview
This project implements image quantization in RGB and YUV color spaces using two modes:
Uniform Quantization (M = 1) – Equal-sized intervals for each channel.
Smart Quantization (M = 2) – Adaptive, non-uniform intervals based on image data.

The program takes a raw .rgb image, applies quantization, and shows:
Left: Original image
Right: Quantized image

It also prints Mean Squared Error (MSE) to the terminal for analysis and supports batch mode to test all possible bit distributions for a given total bit budget 𝑁

How to Compile
javac ImageDisplay.java

How to Run (GUI Mode)
java ImageDisplay <imagepath> <C> <M> <Q1> <Q2> <Q3>

Parameters
Parameter	Description
<imagepath>	Path to the .rgb image file
<C>	Color Space → 1 = RGB, 2 = YUV
<M>	Quantization Mode → 1 = Uniform, 2 = Smart
<Q1>	Bits for R (RGB) or Y (YUV)
<Q2>	Bits for G (RGB) or U (YUV)
<Q3>	Bits for B (RGB) or V (YUV)
How to Run (Batch Mode)

Batch mode enumerates all possible (Q1, Q2, Q3) splits that sum to a given total N.

java ImageDisplay --batch <imagepath> <C> <M> <N>


Example:
To test all distributions where Q1 + Q2 + Q3 = 6, with YUV + Uniform: java ImageDisplay --batch data_sample/lake-forest_352x288.rgb 2 1 6


Output:
The terminal will print a CSV-like table:

N,C,M,Q1,Q2,Q3,MSE
6,2,1,1,1,4,3273.04
6,2,1,2,2,2,858.29
...


This makes it easy to identify which bit distribution yields the lowest error.

Examples (GUI Mode)
Scenario	Command
Full quality (no quantization)	java ImageDisplay data_sample/lake-forest_352x288.rgb 1 1 8 8 8
Reduce all channels to 2 bits (RGB)	java ImageDisplay data_sample/lake-forest_352x288.rgb 1 1 2 2 2
YUV mode, reduce U & V	java ImageDisplay data_sample/lake-forest_352x288.rgb 2 1 8 2 2
Smart quantization (YUV)	java ImageDisplay data_sample/lake-forest_352x288.rgb 2 2 4 2 2


Notes
Default image size is 352 × 288 pixels.
Only raw .rgb files are supported.
The GUI displays original and quantized images side by side.
Terminal output includes MSE values for easy comparison.

Batch mode helps analyze all possible combinations for N = 4, 6, or 8 without manually running each case.
