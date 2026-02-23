# Vehicle Tracking & Geofence System

A simple Spring Boot simulation backend for a Real-Time Vehicle Tracking & Geofence-Based Auto Trip Closure System.

## Technology Stack

- **Java**: 17
- **Spring Boot**: 3.2.2
- **Build Tool**: Maven
- **Database**: MySQL
- **Dependencies**:
  - Spring Web
  - Spring Data JPA
  - Spring Validation
  - Lombok
  - MySQL Driver

## Project Structure

```
src/main/java/com/moveinsync/vehicletracking/
├── VehicleTrackingApplication.java     # Main application class
├── config/
│   └── DataLoader.java                 # Sample data initialization
├── controller/
│   └── LocationController.java         # REST API endpoints
├── dto/
│   ├── ApiResponse.java               # Generic API response
│   └── LocationUpdateRequest.java     # Location update DTO
├── entity/
│   ├── EventLog.java                  # Event logging entity
│   ├── LocationLog.java               # Location history entity
│   ├── OfficeGeofence.java            # Office geofence entity
│   ├── PickupPoint.java               # Pickup point entity
│   ├── Trip.java                      # Trip entity
│   └── Vehicle.java                   # Vehicle entity
├── exception/
│   └── GlobalExceptionHandler.java    # Global error handling
├── repository/
│   ├── EventLogRepository.java
│   ├── LocationLogRepository.java
│   ├── OfficeGeofenceRepository.java
│   ├── PickupPointRepository.java
│   ├── TripRepository.java
│   └── VehicleRepository.java
├── service/
│   └── LocationService.java           # Core business logic
└── util/
    └── GeofenceUtil.java              # Haversine distance calculation
```

## Setup Instructions

### 1. Prerequisites

- Java 17 installed
- Maven installed
- MySQL running on localhost:3306
- MySQL credentials: username=`root`, password=`root` (or update in application.properties)

### 2. Database Setup

The application will automatically create the database `vehicle_tracking` on startup.

Alternatively, create it manually:
```sql
CREATE DATABASE vehicle_tracking;
```

### 3. Build and Run

```bash
# Navigate to project directory
cd moveinsync

# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

The application will start on port 8080.

### 4. Sample Data

On startup, the application automatically loads sample data:
- 1 Office Geofence (Bangalore MG Road area)
- 1 Vehicle (Registration: KA01AB1234)
- 1 Trip (Status: IN_PROGRESS)
- 1 Pickup Point (Status: PENDING)

Check the console logs for the exact coordinates and IDs.

## How It Works

### Chronological Flow

Every time a vehicle sends a GPS update:

1. **Receive Location**: REST API receives location data
2. **Save Log**: Location is saved to `location_logs` table
3. **Validate Trip**: Active trip is loaded and validated
4. **Check Pickup**: System checks if vehicle is within pickup geofence
5. **Check Office**: System checks if vehicle is within office geofence
6. **Generate Events**: Events are generated based on conditions
7. **Save Events**: Events are saved to `event_logs` table

### Pickup Arrival Detection

Conditions:
- Vehicle is within pickup point radius
- Pickup status is "PENDING"

Action:
- Update pickup status to "ARRIVED"
- Create event: `PICKUP_ARRIVED`

### Trip Completion Detection

Conditions:
- Vehicle is within office geofence radius
- Trip status is "IN_PROGRESS"
- Vehicle speed < 5 km/h

Action:
- Update trip status to "COMPLETED"
- Set trip end time
- Create event: `TRIP_COMPLETED`

## API Endpoints

### POST /api/location/update

Send vehicle location update.

**Request Body:**
```json
{
  "vehicleId": 1,
  "tripId": 1,
  "latitude": 12.9520,
  "longitude": 77.5750,
  "speed": 45.0,
  "timestamp": "2026-02-23T10:30:00"
}
```

**Success Response:**
```json
{
  "success": true,
  "message": "Location update processed successfully",
  "data": null
}
```

**Error Response:**
```json
{
  "success": false,
  "message": "Trip not found with ID: 999",
  "data": null
}
```

## Testing the System

### Step 1: Send location at pickup point

This should trigger a `PICKUP_ARRIVED` event.

```bash
curl -X POST http://localhost:8080/api/location/update \
  -H "Content-Type: application/json" \
  -d '{
    "vehicleId": 1,
    "tripId": 1,
    "latitude": 12.9520,
    "longitude": 77.5750,
    "speed": 30.0,
    "timestamp": "2026-02-23T10:30:00"
  }'
```

### Step 2: Send location at office with low speed

This should trigger a `TRIP_COMPLETED` event.

```bash
curl -X POST http://localhost:8080/api/location/update \
  -H "Content-Type: application/json" \
  -d '{
    "vehicleId": 1,
    "tripId": 1,
    "latitude": 12.9716,
    "longitude": 77.5946,
    "speed": 2.0,
    "timestamp": "2026-02-23T11:00:00"
  }'
```

### Step 3: Check Database

Query the database to see logs:

```sql
-- View all location logs
SELECT * FROM location_logs ORDER BY timestamp DESC;

-- View all events
SELECT * FROM event_logs ORDER BY timestamp DESC;

-- Check trip status
SELECT * FROM trips WHERE id = 1;

-- Check pickup point status
SELECT * FROM pickup_points WHERE trip_id = 1;
```

## Configuration

Edit `src/main/resources/application.properties` to customize:

- Database connection
- Server port
- Logging levels
- JPA/Hibernate settings

## Design Decisions

1. **Synchronous Processing**: All logic runs synchronously for simplicity
2. **Single Office Geofence**: System assumes one office location
3. **Haversine Formula**: Simple distance calculation for geofencing
4. **Speed Threshold**: 5 km/h used to detect vehicle stopping
5. **Event Once**: Each event (pickup arrival, trip completion) triggers only once
6. **Constructor Injection**: Used with Lombok @RequiredArgsConstructor
7. **Logging**: Extensive logging for debugging and audit trail

## Notes

- This is a **simulation system**, not production-ready
- No authentication/authorization implemented
- No distributed systems or message queues
- No caching layer
- Simple error handling with global exception handler
- All coordinates are in decimal degrees (latitude/longitude)
- Distance calculations use Earth's radius: 6371 km

## Future Enhancements (Not Implemented)

- WebSocket for real-time updates
- Multiple pickup points per trip
- Driver assignments
- Route optimization
- Historical analytics
- Admin dashboard
- Authentication & authorization
- Rate limiting
- Caching layer
- Message queue for async processing

## Troubleshooting

### Issue: Application won't start
- Check MySQL is running
- Verify database credentials in application.properties
- Ensure port 8080 is available

### Issue: Validation errors
- Ensure all required fields are provided in API request
- Check timestamp format: ISO 8601 (yyyy-MM-ddTHH:mm:ss)

### Issue: Events not triggering
- Verify coordinates are within geofence radius
- Check trip status is "IN_PROGRESS"
- For trip completion, ensure speed < 5 km/h
- Check logs for detailed information

## License

This is a sample project for demonstration purposes.
