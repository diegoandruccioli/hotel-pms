package com.hotelpms.frontdesk.housekeeping.service.impl;

import com.hotelpms.frontdesk.housekeeping.domain.HousekeepingTaskType;
import com.hotelpms.frontdesk.housekeeping.dto.HousekeepingRow;
import com.hotelpms.frontdesk.housekeeping.dto.HousekeepingWorksheetResponse;
import com.hotelpms.frontdesk.housekeeping.service.BusinessDateResolver;
import com.hotelpms.frontdesk.housekeeping.service.HousekeepingWorksheetService;
import com.hotelpms.frontdesk.nightaudit.domain.NightAuditStatus;
import com.hotelpms.frontdesk.nightaudit.repository.NightAuditRunRepository;
import com.hotelpms.frontdesk.reservations.domain.Reservation;
import com.hotelpms.frontdesk.reservations.domain.ReservationLineItem;
import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.repository.ReservationRepository;
import com.hotelpms.frontdesk.rooms.domain.Room;
import com.hotelpms.frontdesk.rooms.domain.RoomStatus;
import com.hotelpms.frontdesk.rooms.repository.RoomRepository;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.domain.StayStatus;
import com.hotelpms.frontdesk.stays.dto.HotelSettingsResponse;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import com.hotelpms.frontdesk.stays.service.HotelSettingsService;
import com.hotelpms.pdftemplate.PdfTemplateRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link HousekeepingWorksheetService}.
 *
 * <p>Composes existing repositories from the rooms, reservations and stays
 * domains — same "no cross-service Feign calls, all owned by this service"
 * approach as {@code DaySheetServiceImpl}.
 */
@Service
@RequiredArgsConstructor
public class HousekeepingWorksheetServiceImpl implements HousekeepingWorksheetService {

    private static final Set<ReservationStatus> ARRIVAL_STATUSES =
            Set.of(ReservationStatus.CONFIRMED, ReservationStatus.PENDING);
    private static final String PDF_TEMPLATE = "housekeeping-worksheet";
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN);
    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(Locale.ITALIAN);

    private final StayRepository stayRepository;
    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final NightAuditRunRepository nightAuditRunRepository;
    private final HotelSettingsService hotelSettingsService;
    private final BusinessDateResolver businessDateResolver;
    private final PdfTemplateRenderer pdfTemplateRenderer;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public HousekeepingWorksheetResponse getWorksheet(final UUID hotelId, final LocalDate date) {
        final LocalDate resolvedDate = date != null ? date : businessDateResolver.resolve(hotelId);
        final HotelSettingsResponse settings = hotelSettingsService.getOrCreate(hotelId);
        final Map<UUID, Room> roomsById = roomRepository.findAllByActiveTrueAndHotelId(hotelId).stream()
                .collect(Collectors.toMap(Room::getId, Function.identity()));

        final List<Reservation> arrivals =
                reservationRepository.findByHotelIdAndCheckInDateAndStatusIn(hotelId, resolvedDate, ARRIVAL_STATUSES);
        final Set<UUID> arrivingRoomIds = arrivingRoomIds(arrivals);

        final Map<UUID, HousekeepingRow> rowsByRoomId = new LinkedHashMap<>();
        final List<HousekeepingRow> stayoverRows = new ArrayList<>();
        classifyCheckedInStays(hotelId, resolvedDate, roomsById, arrivingRoomIds, rowsByRoomId, stayoverRows);

        final List<HousekeepingRow> arrivalRows = buildArrivalRows(arrivals, roomsById, rowsByRoomId.keySet());
        final List<HousekeepingRow> vacantDirtyRows =
                buildRoomStatusRows(hotelId, RoomStatus.DIRTY, HousekeepingTaskType.VACANT_DIRTY, rowsByRoomId.keySet());
        final List<HousekeepingRow> maintenanceRows =
                buildRoomStatusRows(hotelId, RoomStatus.MAINTENANCE, HousekeepingTaskType.MAINTENANCE, rowsByRoomId.keySet());

        final List<HousekeepingRow> rows = new ArrayList<>();
        rows.addAll(sortByRoomNumber(rowsByRoomId.values()));
        rows.addAll(sortByRoomNumber(stayoverRows));
        rows.addAll(sortByRoomNumber(arrivalRows));
        rows.addAll(sortByRoomNumber(vacantDirtyRows));
        rows.addAll(sortByRoomNumber(maintenanceRows));

        return new HousekeepingWorksheetResponse(
                resolvedDate, LocalDateTime.now(), isProvisional(hotelId, resolvedDate),
                settings.hotelName(), rows, summarize(rows));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public byte[] getWorksheetPdf(final UUID hotelId, final LocalDate date) {
        return pdfTemplateRenderer.render(PDF_TEMPLATE, toPdfContext(getWorksheet(hotelId, date)));
    }

    /**
     * Splits every {@code CHECKED_IN} stay into a {@code DEPARTURE} row (added
     * to {@code rowsByRoomId}, keyed for the turnover lookup arrivals need) or
     * a {@code STAYOVER} row (appended to {@code stayoverRows}).
     *
     * <p>A stay whose {@code expectedCheckOutDate} is {@code null} (predates
     * that field) is conservatively treated as {@code STAYOVER} rather than
     * silently dropped from the worksheet — the worst failure here would be a
     * room nobody knows to clean.
     *
     * @param hotelId         the hotel to scope stays to
     * @param date            the worksheet's business date
     * @param roomsById       every active room for the hotel, keyed by id
     * @param arrivingRoomIds room ids with a same-day arrival, for the turnover flag
     * @param rowsByRoomId    mutated: filled with one {@code DEPARTURE} row per room id
     * @param stayoverRows    mutated: appended with one {@code STAYOVER} row per stay
     */
    private void classifyCheckedInStays(
            final UUID hotelId, final LocalDate date, final Map<UUID, Room> roomsById,
            final Set<UUID> arrivingRoomIds, final Map<UUID, HousekeepingRow> rowsByRoomId,
            final List<HousekeepingRow> stayoverRows) {
        for (final Stay stay : stayRepository.findByHotelIdAndStatusWithGuests(hotelId, StayStatus.CHECKED_IN)) {
            final Room room = roomsById.get(stay.getRoomId());
            if (room == null) {
                // Defensive: the room was deactivated after the stay was created.
                continue;
            }
            final int pax = stay.getGuests().size();
            final LocalDate expected = stay.getExpectedCheckOutDate();
            if (expected == null) {
                stayoverRows.add(new HousekeepingRow(room.getRoomNumber(), room.getRoomType().getName(),
                        HousekeepingTaskType.STAYOVER, room.getStatus(), pax, null, false, true));
            } else if (expected.isAfter(date)) {
                stayoverRows.add(new HousekeepingRow(room.getRoomNumber(), room.getRoomType().getName(),
                        HousekeepingTaskType.STAYOVER, room.getStatus(), pax, expected, false, false));
            } else {
                // expected <= date: due today, or overdue — either way it needs a
                // departure clean, so it is never silently dropped either.
                final boolean turnover = arrivingRoomIds.contains(room.getId());
                rowsByRoomId.put(room.getId(), new HousekeepingRow(room.getRoomNumber(), room.getRoomType().getName(),
                        HousekeepingTaskType.DEPARTURE, room.getStatus(), pax, expected, turnover, false));
            }
        }
    }

    private List<HousekeepingRow> buildArrivalRows(
            final List<Reservation> arrivals, final Map<UUID, Room> roomsById, final Set<UUID> departureRoomIds) {
        final List<HousekeepingRow> rows = new ArrayList<>();
        for (final Reservation reservation : arrivals) {
            for (final ReservationLineItem lineItem : reservation.getLineItems()) {
                final UUID roomId = lineItem.getRoomId();
                if (departureRoomIds.contains(roomId)) {
                    // Same-day turnover — already represented by the DEPARTURE row.
                    continue;
                }
                final Room room = roomsById.get(roomId);
                if (room == null) {
                    continue;
                }
                rows.add(new HousekeepingRow(room.getRoomNumber(), room.getRoomType().getName(),
                        HousekeepingTaskType.ARRIVAL_PREP, room.getStatus(), reservation.getExpectedGuests(),
                        null, false, false));
            }
        }
        return rows;
    }

    private List<HousekeepingRow> buildRoomStatusRows(
            final UUID hotelId, final RoomStatus status, final HousekeepingTaskType taskType,
            final Set<UUID> alreadyCoveredRoomIds) {
        return roomRepository.findAllByActiveTrueAndHotelIdAndStatus(hotelId, status).stream()
                // Defensive de-dup: by construction a DEPARTURE/STAYOVER room can't also be
                // DIRTY/MAINTENANCE (housekeeping status is 1:1 with a checked-in stay), but
                // this guards against the invariant ever being violated by a data anomaly.
                .filter(room -> !alreadyCoveredRoomIds.contains(room.getId()))
                .map(room -> new HousekeepingRow(
                        room.getRoomNumber(), room.getRoomType().getName(), taskType, room.getStatus(),
                        0, null, false, false))
                .toList();
    }

    private static Set<UUID> arrivingRoomIds(final List<Reservation> arrivals) {
        final Set<UUID> roomIds = new HashSet<>();
        for (final Reservation reservation : arrivals) {
            for (final ReservationLineItem lineItem : reservation.getLineItems()) {
                roomIds.add(lineItem.getRoomId());
            }
        }
        return roomIds;
    }

    private boolean isProvisional(final UUID hotelId, final LocalDate date) {
        return nightAuditRunRepository.findByHotelIdAndBusinessDate(hotelId, date.minusDays(1))
                .map(run -> run.getStatus() != NightAuditStatus.COMPLETED)
                .orElse(true);
    }

    private static Map<HousekeepingTaskType, Integer> summarize(final List<HousekeepingRow> rows) {
        final Map<HousekeepingTaskType, Integer> summary = new EnumMap<>(HousekeepingTaskType.class);
        for (final HousekeepingRow row : rows) {
            summary.merge(row.taskType(), 1, Integer::sum);
        }
        return summary;
    }

    private static List<HousekeepingRow> sortByRoomNumber(final Collection<HousekeepingRow> rows) {
        return rows.stream().sorted(Comparator.comparing(HousekeepingRow::roomNumber)).toList();
    }

    private Map<String, Object> toPdfContext(final HousekeepingWorksheetResponse worksheet) {
        final Map<String, Object> context = new LinkedHashMap<>();
        context.put("hotelName", worksheet.hotelName() != null ? worksheet.hotelName() : "Hotel");
        context.put("dateFormatted", worksheet.date().format(DATE_FMT));
        context.put("provisional", worksheet.provisional());
        context.put("generatedAtFormatted", worksheet.generatedAt().format(TIMESTAMP_FMT));
        context.put("departureRows", toPdfRows(worksheet, HousekeepingTaskType.DEPARTURE));
        context.put("stayoverRows", toPdfRows(worksheet, HousekeepingTaskType.STAYOVER));
        context.put("arrivalRows", toPdfRows(worksheet, HousekeepingTaskType.ARRIVAL_PREP));
        context.put("vacantDirtyRows", toPdfRows(worksheet, HousekeepingTaskType.VACANT_DIRTY));
        context.put("maintenanceRows", toPdfRows(worksheet, HousekeepingTaskType.MAINTENANCE));
        return context;
    }

    private List<Map<String, Object>> toPdfRows(
            final HousekeepingWorksheetResponse worksheet, final HousekeepingTaskType taskType) {
        return worksheet.rows().stream()
                .filter(row -> row.taskType() == taskType)
                .map(row -> {
                    final Map<String, Object> map = new LinkedHashMap<>();
                    map.put("roomNumber", row.roomNumber());
                    map.put("roomTypeName", row.roomTypeName());
                    map.put("currentStatus", row.currentStatus());
                    map.put("pax", row.pax());
                    map.put("turnover", row.turnover());
                    map.put("departureDateUnknown", row.departureDateUnknown());
                    return map;
                })
                .toList();
    }
}
