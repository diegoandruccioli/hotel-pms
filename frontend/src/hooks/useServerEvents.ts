import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { queryKeys } from '../lib/queryKeys';

const EVENTS_STREAM_URL = '/api/v1/events/stream';

const ROOM_EVENT_TYPES = ['ROOM_STATUS_CHANGED', 'CHECK_IN', 'CHECK_OUT'] as const;
type RoomEventType = (typeof ROOM_EVENT_TYPES)[number];

interface RoomEvent {
  type: RoomEventType;
  timestamp: string;
}

function isRoomEvent(value: unknown): value is RoomEvent {
  return (
    typeof value === 'object' &&
    value !== null &&
    'type' in value &&
    typeof (value as { type: unknown }).type === 'string' &&
    ROOM_EVENT_TYPES.includes((value as { type: RoomEventType }).type)
  );
}

/**
 * Subscribes to the gateway's realtime room-status stream (T-GW-09) for the
 * lifetime of the mounting component and invalidates the affected React
 * Query caches on each event — no component reads the event payload
 * directly, they just refetch. Meant to be mounted once, globally (see
 * `MainLayout.tsx`), not per-page.
 *
 * The browser's native `EventSource` auto-reconnects on drop without any
 * logic needed here, and ignores SSE comment lines (the server's keep-alive
 * heartbeat) — `onmessage` only fires for real `data:` events.
 */
export function useServerEvents(): void {
  const queryClient = useQueryClient();

  useEffect(() => {
    const source = new EventSource(EVENTS_STREAM_URL, { withCredentials: true });

    source.onmessage = (message: MessageEvent<string>) => {
      let parsed: unknown;
      try {
        parsed = JSON.parse(message.data);
      } catch {
        return;
      }
      if (!isRoomEvent(parsed)) {
        return;
      }

      queryClient.invalidateQueries({ queryKey: queryKeys.rooms.all });
      if (parsed.type === 'CHECK_IN' || parsed.type === 'CHECK_OUT') {
        queryClient.invalidateQueries({ queryKey: queryKeys.reservations.all });
        queryClient.invalidateQueries({ queryKey: queryKeys.stays.all });
      }
    };

    return () => source.close();
  }, [queryClient]);
}
