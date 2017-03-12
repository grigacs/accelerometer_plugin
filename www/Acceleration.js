function clearInput(x){
    var y = [];
    var z = x.replace('[', '');
    z = z.replace(']', '');
    y = z.split(', ');
    return y;
}

var Acceleration = function(timestamp, dataX, dataY, dataZ) {

    this.timestamp = timestamp || (new Date()).getTime();
    this.dataX = clearInput(dataX);
    this.dataY = clearInput(dataY);
    this.dataZ = clearInput(dataZ);

};

module.exports = Acceleration;