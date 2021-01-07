'use strict';

import { NativeEventEmitter, NativeModules } from 'react-native';

const { BMDPedometer } = NativeModules;

const PedometerEmitter = new NativeEventEmitter(BMDPedometer);

export default {
  /**
   * check availability of step counter
   */
  isStepCountingAvailable: callback => {
    BMDPedometer.isStepCountingAvailable(callback);
  },

  /**
   * check availability of distance counter
   */
  isDistanceAvailable: callback => {
    BMDPedometer.isDistanceAvailable(callback);
  },

  /**
   * check availability of floor counter
   */
  isFloorCountingAvailable: callback => {
    BMDPedometer.isFloorCountingAvailable(callback);
  },

  /**
   * check availability of pace
   */
  isPaceAvailable: callback => {
    BMDPedometer.isPaceAvailable(callback);
  },

  /**
   * check availability of cadence
   */
  isCadenceAvailable: callback => {
    BMDPedometer.isCadenceAvailable(callback);
  },

  /**
   * start pedometer
   */
  startPedometerUpdatesFromDate: (date, listener) => {
    PedometerEmitter.addListener('pedometerDataDidUpdate', listener);
    BMDPedometer.startPedometerUpdatesFromDate(date);
  },

  /**
   * query pedometer
   */
  queryPedometerDataBetweenDates: (startDate, endDate, callback) => {
    BMDPedometer.queryPedometerDataBetweenDates(startDate, endDate, callback);
  },

  /**
   * stop pedometer
   */
  stopPedometerUpdates: () => {
    PedometerEmitter.removeListener('pedometerDataDidUpdate');
    BMDPedometer.stopPedometerUpdates();
  },
  /**
   * try something
   */
  tryAThing: callback => {
    BMDPedometer.tryAThing(callback);
  },
};
