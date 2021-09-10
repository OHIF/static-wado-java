const http = require("http");
const fs = require('fs').promises;

const host = 'localhost';
const port = 5000;
const rootDir = '/microscopy';

const getAccept = function(req,url) {

}

// GZip path response - returns a GZipped version of the raw file if available, otherwise base

// Raw frames object response type - open up the non-gzipped object, read the multipart header
// and stream the rest as raw data.

// StudyUID response type - read the study UID and return the internal object

// Study query response type - read ALL the study UIDs and return the matching ones

// Rendered response type - read the raw response type, return it if JPEG, otherwise convert to 
// the requested type

// Binary DICOM response type - read the JSON + the streamed data and return responses

// POST data responsee type - read the POSTED data, write to a directory of raw DICOM,
// call StaticWado on the raw DICOM, in the extend study mode

// Client response type - if path isn't prefixed with dicomweb path, then server up client URLs

const requestListener = function (req, res) {
    console.log(`Request url is ${req.url} for accept ${req.headers['accept']}`);
    // console.dir(req);
    const url = new URL(req.url, 'http://localhost');
    const urlBase = url.pathname;
    const pathName = urlBase==='/' && '/index.html' || urlBase;

    // Iterate through listeners based on the path matching
    fs.readFile(rootDir + pathName)
        .then(contents => {
            res.setHeader("Content-Type", "text/html");
            res.writeHead(200);
            res.end(contents);
        })
        .catch(err => {
            console.log(`File not found ${pathName}`);
            res.writeHead(404);
            res.end(`File not found ${pathName}`);
            return;
        });
};

const server = http.createServer(requestListener);
server.listen(port, host, () => {
    console.log(`Server is running on http://${host}:${port}`);
});

