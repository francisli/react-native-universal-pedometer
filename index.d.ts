export interface PedometerInterface {
  startDate: number;
  endDate: number;
  numberOfSteps: number;
  distance: number;
}

type Callback = (error: string | null, available: boolean) => any;

type Listener = (data: PedometerInterface | null) => any;

type QueryCallback = (error: string | null, data: PedometerInterface | null) => any;

declare const _default: {
  isStepCountingAvailable: (callback: Callback) => void;
  isDistanceAvailable: (callback: Callback) => void;
  isFloorCountingAvailable: (callback: Callback) => void;
  isPaceAvailable: (callback: Callback) => void;
  isCadenceAvailable: (callback: Callback) => void;
  startPedometerUpdatesFromDate: (date: number, listener: Listener) => void;
  queryPedometerDataBetweenDates: (startDate: number, endDate: number, callback: QueryCallback) => void;
  stopPedometerUpdates: () => void;
  tryAThing: (callback: Callback) => void;
};

export default _default;
