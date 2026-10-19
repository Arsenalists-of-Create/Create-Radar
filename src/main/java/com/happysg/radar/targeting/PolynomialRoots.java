package com.happysg.radar.targeting;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds real roots of polynomials up to degree four
 * within a bounded interval.
 *
 * Coefficients are ordered from constant to highest degree.
 */
public final class PolynomialRoots {
    private PolynomialRoots() {}

    public static double[] inRange(double[] coefficients, double minimum, double maximum) {
        if (coefficients == null
                || coefficients.length < 2
                || coefficients.length > 5
                || !Double.isFinite(minimum)
                || !Double.isFinite(maximum)
                || minimum >= maximum) {
            return new double[0];
        }

        for (double value : coefficients) {
            if (!Double.isFinite(value)) {
                return new double[0];
            }
        }

        int degree = coefficients.length - 1;
        while (degree > 0 && coefficients[degree] == 0.0) {
            degree--;
        }

        if (degree == 0) {
            return new double[0];
        }

        List<Double> roots = new ArrayList<>(degree);
        isolate(coefficients, degree, minimum, maximum, roots);
        double[] result = new double[roots.size()];

        for (int i = 0; i < result.length; i++) {
            result[i] = roots.get(i);
        }

        return result;
    }

    private static void isolate(double[] polynomial, int degree, double minimum, double maximum, List<Double> roots) {
        if (degree == 1) {
            double root = -polynomial[0] / polynomial[1];

            if (Double.isFinite(root) && root >= minimum && root <= maximum) {
                append(roots, root, minimum, maximum);
            }
            return;
        }

        double[] derivative = new double[degree];

        for (int i = 1; i <= degree; i++) {
            derivative[i - 1] = i * polynomial[i];
        }

        List<Double> stationary = new ArrayList<>(degree - 1);
        isolate(derivative, degree - 1, minimum, maximum, stationary);

        double left = minimum;
        double leftValue = evaluate(polynomial, degree, left);

        if (nearZero(polynomial, degree, left, leftValue)) {
            append(roots, left, minimum, maximum);
        }

        for (int i = 0; i <= stationary.size(); i++) {
            double right = i == stationary.size() ? maximum : stationary.get(i);
            double rightValue = evaluate(polynomial, degree, right);

            if (oppositeSigns(leftValue, rightValue)) {
                double a = left;
                double b = right;
                double fa = leftValue;

                for (int iteration = 0; iteration < 70; iteration++) {
                    double mid = a + (b - a) * 0.5;

                    if (mid == a || mid == b) {
                        break;
                    }

                    double fm = evaluate(polynomial, degree, mid);

                    if (fm == 0.0) {
                        a = mid;
                        b = mid;
                        break;
                    }

                    if (oppositeSigns(fa, fm)) {
                        b = mid;
                    } else {
                        a = mid;
                        fa = fm;
                    }
                }

                append(roots, a + (b - a) * 0.5, minimum, maximum);
            }

            if (nearZero(polynomial, degree, right, rightValue)) {
                append(roots, right, minimum, maximum);
            }

            left = right;
            leftValue = rightValue;
        }
    }

    private static double evaluate(double[] polynomial, int degree, double x) {
        double value = polynomial[degree];
        for (int i = degree - 1; i >= 0; i--) {
            value = Math.fma(value, x, polynomial[i]);
        }

        return value;
    }

    private static boolean oppositeSigns(double a, double b) {
        return (a < 0.0 && b > 0.0) || (a > 0.0 && b < 0.0);
    }

    private static boolean nearZero(double[] polynomial, int degree, double x, double actual) {
        if (!Double.isFinite(actual)) {
            return false;
        }

        double scale = Math.abs(polynomial[degree]);
        double absoluteX = Math.abs(x);

        for (int i = degree - 1; i >= 0; i--) {
            scale = Math.fma(scale, absoluteX, Math.abs(polynomial[i]));
        }

        return Math.abs(actual) <= 1.0e-13 * Math.max(1.0, scale);
    }

    private static void append(List<Double> roots, double root, double minimum, double maximum) {
        if (!Double.isFinite(root)) {
            return;
        }

        double separation = 1.0e-6 * Math.max(1.0, maximum - minimum);

        if (!roots.isEmpty() && Math.abs(root - roots.get(roots.size() - 1)) <= separation) {
            return;
        }

        roots.add(root);
    }
}