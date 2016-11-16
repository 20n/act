
library(rmongodb)
library(wordcloud)

kArgs <- commandArgs(trailingOnly = TRUE)

input.inchi <- kArgs[1]
output.file <- kArgs[2]
host <- kArgs[3]
port <- kArgs[4]
mongo <- mongo.create(paste(host, port, sep = ":"))

query <- list(InChI=input.inchi)

result <- mongo.find.one(mongo, "actv01.chemicals", query)

l <- mongo.bson.to.list(result)

usage.terms.urls <- l$xref$BING$metadata$usage_terms

getUsageTerm <- function(x) x$usage_term
getFreq <- function(x) length(x$urls)

terms <- sapply(usage.terms.urls, getUsageTerm)
freq <- sapply(usage.terms.urls, getFreq)
pal2 <- brewer.pal(8,"Dark2")

set.seed(27)

png(output.file)
wordcloud(terms,freq, scale=c(8,.2),min.freq=1, max.words=30, random.order=FALSE, rot.per=0, colors=pal2)
dev.off()