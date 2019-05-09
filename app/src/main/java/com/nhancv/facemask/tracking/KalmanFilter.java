/*
 * MIT License
 *
 * Copyright (c) 2019 BeeSight Soft
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * @author Nhan Cao <nhan.cao@beesightsoft.com>
 */

package com.nhancv.facemask.tracking;

/**
 * https://en.wikipedia.org/wiki/Kalman_filter
 * Kalman filtering, also known as linear quadratic estimation (LQE), is an algorithm that uses a series of measurements
 * observed over time, containing statistical noise and other inaccuracies, and produces estimates of unknown variables
 * that tend to be more accurate than those based on a single measurement alone, by estimating a joint probability
 * distribution over the variables for each timeframe. The filter is named after Rudolf E. Kálmán, one of the primary
 * developers of its theory.
 *
 * DISCRETE KALMAN FILTER
 * time equation
 *
 * {x_k}^{-} = A * x_{k - 1} + B * u_{k - 1}
 * {P_k}^{-} = A * P_{k - 1} * A^{T} + Q
 *
 * measurement equation
 *
 * K_k = {P_k}^{-} * H^{T} ( H * {P_k}^{-} * H^T + R )^{-1}
 * x_k = {x_k}^{-} + K_k ( z_k - H * {x_k}^{-} )
 * P_k = (I - K_k * H) * {P_k}^{-}
 *
 * P_k  is the estimate error.
 * K_k  is the Kalman Gain.
 * R  is the sensor noise
 * Q  is the process noise
 * x_k  is the filtered value
 * z_k  is the measurement
 * A  is the difference equation that relates the state at the previous time step k – 1
 * to the state at the current step k (in the absence of either a driving function or process noise). It’s “the system matrix”
 * B  relates the optional control input u ∈ R to the state x. It’s “the control matrix”
 * H  relates the state to the measurement zk. It’s the matrix that define the position of the tracked system depending on the measurement.
 *
 * In my case, it’s pretty easy :
 *
 * I have only one dimension since I’m only interested in filtering the value given by a single laser ray.
 * The matrix A is thus equal to A = [1].
 * I have no information about the driving forces. So B can directly be sent to 0 since u is also going to be 0.
 * H is the same as A since the system is the same as the observation. To see what is the utility of H look at this
 * http://people.mech.kuleuven.be/~tdelaet/bfl_doc/getting_started_guide/node11.html.
 *
 * So the sort kalman equation:
 * time equation
 *
 * {x_k}^{-} = x_{k - 1}
 * {P_k}^{-} = P_{k - 1} + Q
 *
 * measurement equation
 *
 * K_k = {P_k}^{-} ( {P_k}^{-} + R )^{-1}
 * x_k = {x_k}^{-} + K_k ( z_k - {x_k}^{-} )
 * P_k = (I - K_k) * {P_k}^{-}
 *
 * Ref: https://malcolmmielle.wordpress.com/2015/04/29/kalman-filter/
 */
public class KalmanFilter {
    float _err_measure;
    float _err_estimate;
    float _q;
    float _current_estimate;
    float _last_estimate;
    float _kalman_gain;


    /**
     * KalmanFilter constructor
     *
     * @param mea_e Measurement Uncertainty - How much do we expect to our measurement vary. Ex: 1
     * @param est_e Estimation Uncertainty - Can be initialized with the same value as e_mea since the kalman filter will adjust its value. Ex 1
     * @param q Process Variance - usually a small number between 0.001 and 1 - how fast your measurement moves. Recommended 0.01
     */
    public KalmanFilter(float mea_e, float est_e, float q) {
        _err_measure = mea_e;
        _err_estimate = est_e;
        _q = q;
    }

    public float updateEstimate(float mea) {
        _kalman_gain = _err_estimate / (_err_estimate + _err_measure);
        _current_estimate = _last_estimate + _kalman_gain * (mea - _last_estimate);
        _err_estimate = (float) ((1.0 - _kalman_gain) * _err_estimate + Math.abs(_last_estimate - _current_estimate) * _q);
        _last_estimate = _current_estimate;

        return _current_estimate;
    }

    public void setMeasurementError(float mea_e) {
        _err_measure = mea_e;
    }

    public void setEstimateError(float est_e) {
        _err_estimate = est_e;
    }

    public void setProcessNoise(float q) {
        _q = q;
    }

    public float getKalmanGain() {
        return _kalman_gain;
    }

    public float getEstimateError() {
        return _err_estimate;
    }

}
