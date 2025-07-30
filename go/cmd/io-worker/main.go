package main

import (
	"log"
	"os"

	"github.com/example/temporal-rag/activities/io"
	"go.temporal.io/sdk/client"
	"go.temporal.io/sdk/worker"
)

func main() {
	// Create Temporal client
	c, err := client.Dial(client.Options{
		HostPort: getEnv("TEMPORAL_HOST", "localhost:7233"),
	})
	if err != nil {
		log.Fatalf("Unable to create Temporal client: %v", err)
	}
	defer c.Close()

	// Create worker for IO-bound queue with high concurrency
	w := worker.New(c, "io-bound-queue", worker.Options{
		MaxConcurrentActivityExecutionSize: 200, // High concurrency for IO operations
		WorkerActivitiesPerSecond:          1000,
	})

	// Create and register IO activities
	ioActivities := io.NewIOBoundActivities()
	w.RegisterActivity(ioActivities.ParseCSV)
	w.RegisterActivity(ioActivities.DownloadDocument)
	w.RegisterActivity(ioActivities.UploadToS3)
	w.RegisterActivity(ioActivities.QueryMetadataDatabase)
	w.RegisterActivity(ioActivities.StoreVectorEmbeddings)
	w.RegisterActivity(ioActivities.CallExternalAPI)

	log.Println("Starting IO Worker on queue: io-bound-queue")
	log.Println("Optimized for high-concurrency IO operations (network, disk, database)")
	
	err = w.Run(worker.InterruptCh())
	if err != nil {
		log.Fatalf("Unable to start IO Worker: %v", err)
	}
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}