/** Lets any page tell the nav bar that the set of orders awaiting confirmation changed. */
const EVENT = 'approvals-changed';

export const notifyApprovalsChanged = () => window.dispatchEvent(new Event(EVENT));

export function onApprovalsChanged(fn: () => void) {
  window.addEventListener(EVENT, fn);
  return () => window.removeEventListener(EVENT, fn);
}
