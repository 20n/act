library(mongolite)
library(wordcloud)

kArgs <- commandArgs(trailingOnly = TRUE)

input.inchi <- kArgs[1]
output.file <- kArgs[2]
host <- kArgs[3]
port <- kArgs[4]

options(mongodb = list("host" = paste(host, port, sep = ":")))
databaseName <- "jarvis_2016-12-09"
collectionName <- "chemicals"

db <- mongo(collection = collectionName,
            url = sprintf("mongodb://%s/%s",
                          options()$mongodb$host,
                          databaseName)
)


result <- db$find(sprintf('{"InChI": "%s"}', input.inchi))


usage.terms.urls <- as.data.frame(result$xref$BING$metadata$usage_terms)

terms <- usage.terms.urls$usage_term
freq <- sapply(usage.terms.urls$urls, length)

pal2 <- brewer.pal(8,"Dark2")

set.seed(27)

png(output.file)
wordcloud(terms,freq, scale=c(4,.5),min.freq=1, max.words=30, random.order=FALSE, rot.per=0, colors=pal2)
dev.off()
